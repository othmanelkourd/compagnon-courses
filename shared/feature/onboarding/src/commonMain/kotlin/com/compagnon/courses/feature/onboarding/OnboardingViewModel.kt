package com.compagnon.courses.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.compagnon.courses.core.data.CatalogRepository
import com.compagnon.courses.core.data.CatalogScope
import com.compagnon.courses.core.data.HouseholdRepository
import com.compagnon.courses.core.data.HouseholdSetup
import com.compagnon.courses.core.model.AgeBand
import com.compagnon.courses.core.model.Catalog
import com.compagnon.courses.core.model.DietaryTags
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.model.format.capitalizedFirst
import com.compagnon.courses.engine.SessionContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The onboarding « Mon foyer » (`design/maquette/Onboarding-*.dc.html`): four steps (household, diet and
 * avoidances, essentials, recurring products and rhythm), saved at once with [HouseholdRepository.saveSetup].
 *
 * - [OnboardingMode.FIRST_RUN]: welcome → the four steps → recap. « Plus tard » keeps the defaults.
 * - [OnboardingMode.EDIT]: « Mon foyer » ([OnboardingStep.OVERVIEW]), from which each section is edited and
 *   saved on its own, or the whole questionnaire restarted.
 *
 * Every step can be skipped: it then keeps its saved answer (the defaults on the first launch: eats
 * everything, the catalog's essentials, no recurring product, 7 days). Downloads the catalog when it is
 * missing (the home screen, which usually does it, is not shown before). Failures are values of [state].
 */
class OnboardingViewModel(
    private val mode: OnboardingMode,
    private val households: HouseholdRepository,
    private val catalog: CatalogRepository,
    private val scope: CatalogScope,
) : ViewModel() {

    private val _state = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Loading)
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** The household being answered. [essentials] null: not chosen (the pre-checked defaults are shown). */
    private data class Draft(
        val adults: Int,
        val babies: Int,
        val children: Int,
        val teens: Int,
        val pets: Set<PetChoice>,
        val diet: DietChoice,
        val avoid: Set<AvoidChoice>,
        val essentials: Set<ProductId>?,
        val cadences: Map<ProductId, CadenceChoice>,
        val horizonDays: Int,
        val rhythm: RhythmChoice,
    )

    private data class Screen(
        val step: OnboardingStep,
        /** Where finishing (or closing) the current section goes: « Mon foyer » or the recap; null in the flow. */
        val returnTo: OnboardingStep?,
        val expanded: Set<String> = emptySet(),
        val essentialsQuery: String = "",
        val essentialsResults: List<ProductId> = emptyList(),
        val addingRecurring: Boolean = false,
        val recurringQuery: String = "",
        val recurringResults: List<ProductId> = emptyList(),
        val saving: Boolean = false,
        val error: OnboardingErrorKind? = null,
        val exit: OnboardingExit? = null,
    )

    private var saved: HouseholdSetup? = null
    private var onboarded = false
    private var draft: Draft? = null
    private var screen = Screen(if (mode == OnboardingMode.FIRST_RUN) OnboardingStep.WELCOME else OnboardingStep.OVERVIEW, null)
    private var loadedCatalog: Catalog? = null
    private var products: Map<ProductId, Product> = emptyMap()
    private var catalogLoading = false
    private var catalogMissing = false

    /** Products touched on the essentials grid: they stay visible in a folded aisle. */
    private val touched = LinkedHashSet<ProductId>()
    private var essentialsSearch: Job? = null
    private var recurringSearch: Job? = null
    private var failedAction: (() -> Unit)? = null
    private var prefilled = false

    init {
        load()
    }

    fun onEvent(event: OnboardingEvent) {
        if (draft == null) return
        when (event) {
            OnboardingEvent.Start -> {
                if (mode == OnboardingMode.EDIT) resetToSaved()
                go(OnboardingStep.HOUSEHOLD, returnTo = null)
            }
            OnboardingEvent.Later -> later()
            is OnboardingEvent.Edit -> {
                resetToSaved()
                val from = screen.step.takeIf { it == OnboardingStep.OVERVIEW || it == OnboardingStep.RECAP } ?: return
                go(SECTIONS.getValue(event.section).first(), returnTo = from)
            }
            OnboardingEvent.Back -> back()
            OnboardingEvent.Next -> next()
            OnboardingEvent.Skip -> skip()
            is OnboardingEvent.StepMember -> stepMember(event.kind, event.increment)
            is OnboardingEvent.TogglePet -> update { it.copy(pets = it.pets.toggle(event.pet)) }
            is OnboardingEvent.SelectDiet -> update { it.copy(diet = event.diet) }
            is OnboardingEvent.ToggleAvoid -> {
                if (event.avoid !in implied(draft!!.diet)) update { it.copy(avoid = it.avoid.toggle(event.avoid)) }
            }
            OnboardingEvent.NothingToAvoid -> {
                update { it.copy(avoid = emptySet()) }
                next()
            }
            is OnboardingEvent.SearchEssentials -> searchEssentials(event.query)
            is OnboardingEvent.ToggleEssential -> toggleEssential(ProductId(event.productId))
            is OnboardingEvent.ExpandAisle -> {
                val expanded = screen.expanded
                screen = screen.copy(expanded = if (event.categoryId in expanded) expanded - event.categoryId else expanded + event.categoryId)
                render()
            }
            is OnboardingEvent.SetCadence -> {
                val id = ProductId(event.productId)
                if (id in draft!!.cadences) update { it.copy(cadences = it.cadences + (id to event.cadence)) }
            }
            is OnboardingEvent.RemoveRecurring -> update { it.copy(cadences = it.cadences - ProductId(event.productId)) }
            is OnboardingEvent.AddingRecurring -> {
                recurringSearch?.cancel()
                screen = screen.copy(addingRecurring = event.open, recurringQuery = "", recurringResults = emptyList())
                render()
            }
            is OnboardingEvent.SearchRecurring -> searchRecurring(event.query)
            is OnboardingEvent.AddRecurring -> addRecurring(ProductId(event.productId))
            is OnboardingEvent.SelectRhythm -> selectRhythm(event.choice)
            is OnboardingEvent.StepRhythm -> update { current ->
                val days = (current.horizonDays + if (event.increment) 1 else -1).coerceIn(1, SessionContext.MAX_HORIZON_DAYS)
                current.copy(horizonDays = days, rhythm = RhythmChoice.OTHER)
            }
            is OnboardingEvent.Finish -> if (screen.step == OnboardingStep.RECAP) {
                exit(if (event.startSession) OnboardingExit.START_SESSION else OnboardingExit.HOME)
            }
            OnboardingEvent.Retry -> retry()
            OnboardingEvent.DismissError -> {
                screen = screen.copy(error = null)
                render()
            }
            OnboardingEvent.ExitHandled -> {
                screen = screen.copy(exit = null)
                render()
            }
        }
    }

    // --- Loading ---

    private fun load() {
        viewModelScope.launch {
            try {
                val setup = households.setup()
                onboarded = households.household().onboardedAt != null
                saved = setup
                draft = setup.toDraft()
                render()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "The household could not be read" }
                val defaults = HouseholdSetup(adults = 2)
                saved = defaults
                draft = defaults.toDraft()
                screen = screen.copy(error = OnboardingErrorKind.STORAGE_FAILED)
                render()
            }
        }
        loadCatalog()
    }

    private fun loadCatalog() {
        viewModelScope.launch {
            catalogLoading = true
            catalogMissing = false
            render()
            val loaded = try {
                catalog.catalog() ?: run {
                    catalog.sync(scope.country, scope.locale)
                    catalog.catalog()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.w(e) { "The catalog could not be loaded" }
                null
            }
            catalogLoading = false
            catalogMissing = loaded == null || loaded.products.isEmpty()
            loadedCatalog = loaded
            products = loaded?.products?.associateBy { it.id }.orEmpty()
            render()
        }
    }

    // --- Navigation ---

    private fun go(step: OnboardingStep, returnTo: OnboardingStep?) {
        if (step == OnboardingStep.RECURRING) prefillRecurring()
        screen = screen.copy(
            step = step,
            returnTo = returnTo,
            essentialsQuery = "",
            essentialsResults = emptyList(),
            addingRecurring = false,
            recurringQuery = "",
            recurringResults = emptyList(),
            error = null,
        )
        render()
    }

    private fun next() {
        val step = screen.step
        val returnTo = screen.returnTo
        if (step == OnboardingStep.ESSENTIALS) chooseEssentials()
        when {
            step == OnboardingStep.WELCOME -> go(OnboardingStep.HOUSEHOLD, returnTo = null)
            step == OnboardingStep.HOUSEHOLD && draft!!.let { it.adults + it.babies + it.children + it.teens } < 1 -> Unit
            returnTo != null && SECTIONS.values.any { it.last() == step } -> save { go(returnTo, returnTo = null) }
            step == OnboardingStep.RHYTHM -> save { go(OnboardingStep.RECAP, returnTo = null) }
            else -> FLOW.getOrNull(FLOW.indexOf(step) + 1)?.let { go(it, returnTo = returnTo) }
        }
    }

    private fun back() {
        val step = screen.step
        val returnTo = screen.returnTo
        when {
            step == OnboardingStep.OVERVIEW || step == OnboardingStep.RECAP -> exit(OnboardingExit.HOME)
            step == OnboardingStep.WELCOME -> Unit
            returnTo != null && SECTIONS.values.any { it.first() == step } -> {
                resetToSaved()
                go(returnTo, returnTo = null)
            }
            step == OnboardingStep.HOUSEHOLD -> {
                resetToSaved()
                go(if (mode == OnboardingMode.FIRST_RUN) OnboardingStep.WELCOME else OnboardingStep.OVERVIEW, returnTo = null)
            }
            else -> FLOW.getOrNull(FLOW.indexOf(step) - 1)?.let { go(it, returnTo = returnTo) }
        }
    }

    /** « Passer »: the step keeps its saved answer (the default on the first launch). */
    private fun skip() {
        val reference = saved ?: return
        update { current ->
            when (screen.step) {
                OnboardingStep.DIET -> current.copy(diet = reference.diet.toUi())
                OnboardingStep.AVOID -> current.copy(avoid = reference.avoid.mapTo(LinkedHashSet()) { it.toUi() })
                OnboardingStep.ESSENTIALS -> current.copy(essentials = reference.essentials)
                OnboardingStep.RECURRING -> current.copy(cadences = reference.cadences.orEmpty().toChoices())
                OnboardingStep.RHYTHM -> current.copy(
                    horizonDays = reference.usualHorizonDays,
                    rhythm = rhythmChoiceOf(reference.usualHorizonDays),
                )
                else -> current
            }
        }
        if (screen.step == OnboardingStep.ESSENTIALS) {
            // Skipped: not chosen (the catalog's essentials stay, or the saved choice).
            go(OnboardingStep.RECURRING, returnTo = screen.returnTo)
        } else {
            next()
        }
    }

    private fun later() {
        if (mode != OnboardingMode.FIRST_RUN) return
        run(saving = true) {
            households.skipSetup()
            onboarded = true
            exit(OnboardingExit.HOME)
        }
    }

    private fun exit(exit: OnboardingExit) {
        screen = screen.copy(exit = exit)
        render()
    }

    // --- Answers ---

    private fun stepMember(kind: MemberKind, increment: Boolean) = update { current ->
        val delta = if (increment) 1 else -1
        when (kind) {
            MemberKind.ADULTS -> current.copy(adults = (current.adults + delta).coerceIn(1, MAX_MEMBERS))
            MemberKind.BABIES -> current.copy(babies = (current.babies + delta).coerceIn(0, MAX_MEMBERS))
            MemberKind.CHILDREN -> current.copy(children = (current.children + delta).coerceIn(0, MAX_MEMBERS))
            MemberKind.TEENS -> current.copy(teens = (current.teens + delta).coerceIn(0, MAX_MEMBERS))
        }
    }

    private fun toggleEssential(id: ProductId) {
        if (id !in products) return
        touched += id
        update { current ->
            val chosen = current.essentials ?: defaults(current)
            current.copy(essentials = if (id in chosen) chosen - id else chosen + id)
        }
    }

    /** « Continuer » on the essentials: the shown choice becomes the household's. */
    private fun chooseEssentials() {
        val current = draft ?: return
        if (current.essentials == null && loadedCatalog != null) draft = current.copy(essentials = defaults(current))
    }

    private fun defaults(current: Draft): Set<ProductId> =
        loadedCatalog?.let { defaultEssentials(it, current.toSetup()) }.orEmpty()

    /**
     * On the first launch, the recurring step starts with up to three chosen essentials bought most often,
     * each with the closest rhythm: the household corrects or removes them.
     */
    private fun prefillRecurring() {
        val current = draft ?: return
        if (mode != OnboardingMode.FIRST_RUN || current.cadences.isNotEmpty() || saved?.cadences.orEmpty().isNotEmpty()) return
        if (prefilled) return
        prefilled = true
        val essentials = current.essentials ?: defaults(current)
        val eligibility = Eligibility.of(current.toSetup())
        val picks = essentials.mapNotNull { products[it] }
            .filter { eligibility.accepts(it) && it.market.typicalRepurchaseDays != null }
            .sortedWith(compareBy<Product>({ it.market.typicalRepurchaseDays }).then(byPopularity))
            .let { sorted ->
                // A baby's or a pet's product when there is one: the rhythms people know best.
                val special = sorted.filter { DietaryTags.BABY in it.tags || DietaryTags.PET in it.tags }.take(2)
                (sorted.filterNot { it in special }.take(PREFILLED_RECURRING - special.size) + special)
            }
        draft = current.copy(
            cadences = picks.associateTo(LinkedHashMap()) { it.id to nearestCadence(it.market.typicalRepurchaseDays) },
        )
    }

    private fun addRecurring(id: ProductId) {
        val product = products[id] ?: return
        recurringSearch?.cancel()
        screen = screen.copy(addingRecurring = false, recurringQuery = "", recurringResults = emptyList())
        update { current ->
            if (id in current.cadences) current
            else current.copy(cadences = current.cadences + (id to nearestCadence(product.market.typicalRepurchaseDays)))
        }
    }

    private fun selectRhythm(choice: RhythmChoice) = update { current ->
        val days = when (choice) {
            RhythmChoice.WEEK -> CadenceChoice.WEEK.days
            RhythmChoice.TWO_WEEKS -> CadenceChoice.TWO_WEEKS.days
            RhythmChoice.MONTH -> CadenceChoice.MONTH.days
            RhythmChoice.OTHER -> current.horizonDays
        }
        current.copy(horizonDays = days, rhythm = choice)
    }

    private fun searchEssentials(query: String) {
        screen = screen.copy(essentialsQuery = query)
        essentialsSearch?.cancel()
        if (query.isBlank()) {
            screen = screen.copy(essentialsResults = emptyList())
            render()
            return
        }
        render()
        essentialsSearch = viewModelScope.launch {
            val results = search(query)
            if (screen.essentialsQuery == query) {
                screen = screen.copy(essentialsResults = results)
                render()
            }
        }
    }

    private fun searchRecurring(query: String) {
        screen = screen.copy(recurringQuery = query)
        recurringSearch?.cancel()
        if (query.isBlank()) {
            screen = screen.copy(recurringResults = emptyList())
            render()
            return
        }
        render()
        recurringSearch = viewModelScope.launch {
            val results = search(query)
            if (screen.recurringQuery == query) {
                screen = screen.copy(recurringResults = results)
                render()
            }
        }
    }

    private suspend fun search(query: String): List<ProductId> {
        val current = draft ?: return emptyList()
        val eligibility = Eligibility.of(current.toSetup())
        return try {
            catalog.search(query).filter { eligibility.accepts(it) }.map { it.id }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Search failed" }
            emptyList()
        }
    }

    // --- Saving ---

    private fun save(then: () -> Unit) {
        val current = draft ?: return
        run(saving = true) {
            val setup = current.toSetup()
            households.saveSetup(setup)
            saved = households.setup()
            onboarded = true
            draft = saved!!.toDraft().copy(rhythm = current.rhythm)
            then()
        }
    }

    private fun run(saving: Boolean, block: suspend () -> Unit) {
        if (screen.saving) return
        screen = screen.copy(saving = saving, error = null)
        render()
        viewModelScope.launch {
            try {
                block()
                failedAction = null
                screen = screen.copy(saving = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "The household could not be saved" }
                failedAction = { run(saving, block) }
                screen = screen.copy(saving = false, error = OnboardingErrorKind.STORAGE_FAILED)
            }
            render()
        }
    }

    private fun retry() {
        val action = failedAction
        when {
            action != null -> {
                failedAction = null
                action()
            }
            catalogMissing -> loadCatalog()
            else -> {
                screen = screen.copy(error = null)
                render()
            }
        }
    }

    private fun resetToSaved() {
        val reference = saved ?: return
        val rhythm = draft?.rhythm
        draft = reference.toDraft().let { if (rhythm != null && rhythmChoiceOf(it.horizonDays) == RhythmChoice.OTHER) it.copy(rhythm = rhythm) else it }
        touched.clear()
    }

    // --- Rendering ---

    private fun update(change: (Draft) -> Draft) {
        val current = draft ?: return
        draft = change(current)
        render()
    }

    private fun render() {
        val current = draft ?: return
        val setup = current.toSetup()
        val step = screen.step
        val number = STEP_NUMBERS[step]
        _state.value = OnboardingUiState.Ready(
            mode = mode,
            step = step,
            stepNumber = number,
            stepCount = STEP_COUNT,
            canSkip = screen.returnTo == null && number != null && step != OnboardingStep.HOUSEHOLD,
            closeInsteadOfBack = screen.returnTo == OnboardingStep.OVERVIEW,
            household = HouseholdStepUi(
                adults = current.adults,
                babies = current.babies,
                children = current.children,
                teens = current.teens,
                pets = PetChoice.entries.filter { it in current.pets },
                maxMembers = MAX_MEMBERS,
            ),
            diet = current.diet,
            avoid = AvoidChoice.entries.map { choice ->
                val locked = choice in implied(current.diet)
                AvoidOptionUi(choice, checked = locked || choice in current.avoid, locked = locked)
            },
            essentials = essentialsUi(current, setup),
            recurring = RecurringStepUi(
                rows = current.cadences.mapNotNull { (id, cadence) ->
                    products[id]?.let { RecurringRowUi(id.value, nameOf(it), cadence) }
                },
                adding = screen.addingRecurring,
                query = screen.recurringQuery,
                results = screen.recurringResults.mapNotNull { id ->
                    products[id]?.let { ProductChipUi(id.value, nameOf(it), checked = id in current.cadences) }
                },
            ),
            rhythm = RhythmStepUi(current.horizonDays, current.rhythm, SessionContext.MAX_HORIZON_DAYS),
            summary = summaryOf(
                setup.copy(essentials = current.essentials ?: setup.essentials),
                loadedCatalog,
                onboarded,
            ),
            saving = screen.saving,
            error = screen.error,
            exit = screen.exit,
        )
    }

    private fun essentialsUi(current: Draft, setup: HouseholdSetup): EssentialsStepUi {
        val loaded = loadedCatalog
        if (loaded == null) {
            return EssentialsStepUi(screen.essentialsQuery, emptyList(), emptyList(), 0, catalogLoading, catalogMissing)
        }
        val eligibility = Eligibility.of(setup)
        val checked = (current.essentials ?: defaultEssentials(loaded, setup)).filterTo(HashSet()) { id ->
            products[id]?.let(eligibility::accepts) == true
        }
        val byCategory = loaded.products.filter(eligibility::accepts).groupBy { it.categoryId }
        val aisles = loaded.categories.sortedWith(compareBy({ it.position }, { it.id.value })).mapNotNull { category ->
            val all = byCategory[category.id]?.sortedWith(byPopularity) ?: return@mapNotNull null
            val expanded = category.id.value in screen.expanded
            val shown = if (expanded || all.size <= FOLDED_AISLE) {
                all
            } else {
                val top = all.take(FOLDED_AISLE).toSet()
                all.filter { it in top || it.id in checked || it.id in touched }
            }
            EssentialsAisleUi(
                categoryId = category.id.value,
                title = category.name,
                items = shown.map { ProductChipUi(it.id.value, nameOf(it), it.id in checked) },
                total = all.size,
                expanded = expanded,
            )
        }
        return EssentialsStepUi(
            query = screen.essentialsQuery,
            aisles = aisles,
            results = screen.essentialsResults.mapNotNull { id ->
                products[id]?.let { ProductChipUi(id.value, nameOf(it), id in checked) }
            },
            checkedCount = checked.size,
            catalogLoading = false,
            catalogMissing = false,
        )
    }

    private fun nameOf(product: Product): String = product.name.text.capitalizedFirst()

    // --- Conversions ---

    private fun HouseholdSetup.toDraft(): Draft = Draft(
        adults = adults.coerceIn(1, MAX_MEMBERS),
        babies = children.count { it == AgeBand.BABY },
        children = children.count { it == AgeBand.CHILD },
        teens = children.count { it == AgeBand.TEEN },
        pets = pets.mapTo(LinkedHashSet()) { it.toUi() },
        diet = diet.toUi(),
        avoid = avoid.mapTo(LinkedHashSet()) { it.toUi() },
        essentials = essentials,
        cadences = cadences.orEmpty().toChoices(),
        horizonDays = usualHorizonDays,
        rhythm = rhythmChoiceOf(usualHorizonDays),
    )

    private fun Map<ProductId, Int>.toChoices(): Map<ProductId, CadenceChoice> =
        entries.associateTo(LinkedHashMap()) { (id, days) -> id to nearestCadence(days) }

    private fun Draft.toSetup(): HouseholdSetup {
        val eligibility = Eligibility(
            diet = diet.toModel(),
            avoid = avoid.mapTo(LinkedHashSet()) { it.toModel() },
            hasBaby = babies > 0,
            hasPet = pets.isNotEmpty(),
        )
        // Products no longer allowed by the diet (changed after choosing them) are dropped.
        fun allowed(id: ProductId) = products[id]?.let(eligibility::accepts) ?: true
        return HouseholdSetup(
            adults = adults,
            children = List(babies) { AgeBand.BABY } + List(children) { AgeBand.CHILD } + List(teens) { AgeBand.TEEN },
            pets = pets.mapTo(LinkedHashSet()) { it.toModel() },
            diet = diet.toModel(),
            // Avoidances implied by the diet are not stored on their own.
            avoid = (avoid - implied(diet)).mapTo(LinkedHashSet()) { it.toModel() },
            essentials = essentials?.filterTo(LinkedHashSet(), ::allowed),
            cadences = cadences.filterKeys(::allowed).mapValues { it.value.days },
            usualHorizonDays = horizonDays,
        )
    }

    private fun implied(diet: DietChoice): Set<AvoidChoice> =
        DietaryTags.impliedBy(diet.toModel()).mapTo(LinkedHashSet()) { it.toUi() }

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

    private companion object {
        val log = Logger.withTag("OnboardingViewModel")
        const val MAX_MEMBERS = 12
        const val STEP_COUNT = 4
        const val FOLDED_AISLE = 6
        const val PREFILLED_RECURRING = 3

        /** The questionnaire, in order. */
        val FLOW = listOf(
            OnboardingStep.HOUSEHOLD,
            OnboardingStep.DIET,
            OnboardingStep.AVOID,
            OnboardingStep.ESSENTIALS,
            OnboardingStep.RECURRING,
            OnboardingStep.RHYTHM,
        )

        val SECTIONS: Map<HouseholdSection, List<OnboardingStep>> = mapOf(
            HouseholdSection.HOUSEHOLD to listOf(OnboardingStep.HOUSEHOLD),
            HouseholdSection.FOOD to listOf(OnboardingStep.DIET, OnboardingStep.AVOID),
            HouseholdSection.ESSENTIALS to listOf(OnboardingStep.ESSENTIALS),
            HouseholdSection.RHYTHM to listOf(OnboardingStep.RECURRING, OnboardingStep.RHYTHM),
        )

        val STEP_NUMBERS: Map<OnboardingStep, Int> = mapOf(
            OnboardingStep.HOUSEHOLD to 1,
            OnboardingStep.DIET to 2,
            OnboardingStep.AVOID to 2,
            OnboardingStep.ESSENTIALS to 3,
            OnboardingStep.RECURRING to 4,
            OnboardingStep.RHYTHM to 4,
        )
    }
}
