package com.compagnon.courses.android.questions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.AppIcons
import com.compagnon.courses.android.ui.components.BlockDivider
import com.compagnon.courses.android.ui.components.BottomLinks
import com.compagnon.courses.android.ui.components.Chip
import com.compagnon.courses.android.ui.components.ChipTone
import com.compagnon.courses.android.ui.components.CompagnonIconButton
import com.compagnon.courses.android.ui.components.FreeStepper
import com.compagnon.courses.android.ui.components.Hint
import com.compagnon.courses.android.ui.components.IconButtonStyle
import com.compagnon.courses.android.ui.components.OptionTone
import com.compagnon.courses.android.ui.components.Overline
import com.compagnon.courses.android.ui.components.PrimaryButton
import com.compagnon.courses.android.ui.components.QuantityChoiceTile
import com.compagnon.courses.android.ui.components.ResponseOption
import com.compagnon.courses.android.ui.components.SecondaryButton
import com.compagnon.courses.android.ui.components.TextActionButton
import com.compagnon.courses.android.ui.components.resolve
import com.compagnon.courses.android.ui.components.softShadow
import com.compagnon.courses.android.ui.illustration.Illustration
import com.compagnon.courses.android.ui.illustration.LeafPattern
import com.compagnon.courses.android.ui.illustration.PatternLeaf
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonShapes
import com.compagnon.courses.android.ui.theme.CompagnonSpacing
import com.compagnon.courses.android.ui.theme.CompagnonType
import com.compagnon.courses.feature.questionflow.NuancedAnswer
import com.compagnon.courses.feature.questionflow.ProductCardUi
import com.compagnon.courses.feature.questionflow.QuantityValue
import com.compagnon.courses.feature.questionflow.QuestionEvent
import com.compagnon.courses.feature.questionflow.QuestionUiState
import com.compagnon.courses.feature.questionflow.UnitOptionUi
import kotlinx.coroutines.launch

/** Quick quantities per row: three on one row as in the mockup, four on two rows (same rule on iOS). */
internal fun presetColumns(count: Int): Int = if (count == 4) 2 else count.coerceIn(1, 3)

/** The image banner of a product card: [height] high with an [image] of that size. */
internal data class CardBannerSize(val height: Dp, val image: Dp)

/**
 * The card's image banner for the system font scale: 100 high with an 84 image, smaller (72 / 56) with a larger
 * font, and gone beyond 1.45 so that the name, the question and the quantities keep the room (same on iOS).
 */
internal fun cardBannerSize(fontScale: Float): CardBannerSize? = when {
    fontScale <= 1.15f -> CardBannerSize(height = 100.dp, image = 84.dp)
    fontScale <= 1.45f -> CardBannerSize(height = 72.dp, image = 56.dp)
    else -> null
}

/**
 * A full-screen product card: « Il vous faut du lait ? ». Swipe right or « Oui · 2 bouteilles » = yes with the
 * stepper's quantity, a preset = yes with that quantity, swipe left or « Non » = no, « Plus d'options » = the
 * nuanced answers. Every answer makes the card fly off while the next one, behind it with its image
 * ([QuestionUiState.Card.nextImageKey]), comes forward. The answer buttons sit at the bottom, under the thumb,
 * « Annuler » at the bottom left. [popImage]: the product's image pops in (not when it was already on the card
 * behind).
 */
@Composable
internal fun CardStep(state: QuestionUiState.Card, onEvent: (QuestionEvent) -> Unit, popImage: Boolean = true) {
    val card = state.card
    var showOptions by rememberSaveable(card.productId) { mutableStateOf(false) }
    val yesLabel = stringResource(R.string.answer_yes)
    val noLabel = stringResource(R.string.answer_no)
    val cardState = key(state.stepKey()) { rememberAnswerCardState() }
    val banner = cardBannerSize(LocalDensity.current.fontScale)
    // Where the banner sits in the card, so that the card behind shows the next image at the same place.
    var bannerTop by remember { mutableStateOf<Dp?>(null) }
    val answerYes = { quantity: QuantityValue? -> cardState.answer(yes = true) { onEvent(QuestionEvent.Yes(quantity)) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = CompagnonSpacing.Screen)
            .padding(top = 14.dp, bottom = CompagnonSpacing.Bottom),
    ) {
        key(state.stepKey()) {
            SwipeableAnswerCard(
                yesLabel = yesLabel,
                noLabel = noLabel,
                onYes = { onEvent(QuestionEvent.Yes()) },
                onNo = { onEvent(QuestionEvent.No) },
                state = cardState,
                behind = state.nextImageKey?.let { next ->
                    { NextCardPreview(imageKey = next, banner = banner, bannerTop = bannerTop) }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                ProductCardBody(
                    card = card,
                    banner = banner,
                    popImage = popImage,
                    onBannerTop = { bannerTop = it },
                    onPreset = answerYes,
                    onEvent = onEvent,
                )
            }
        }
        SwipeHints(modifier = Modifier.padding(top = 4.dp))
        AnswerButtons(
            noText = noLabel,
            yesText = stringResource(R.string.answer_yes_with_quantity, card.quantity.selected.label),
            onYes = { answerYes(null) },
            onNo = { cardState.answer(yes = false) { onEvent(QuestionEvent.No) } },
            modifier = Modifier.padding(top = 16.dp),
            noIcon = AppIcons.Close,
            yesIcon = AppIcons.Check,
        )
        BottomLinks(
            modifier = Modifier.padding(top = 10.dp),
            start = { UndoAnswerButton(canUndo = state.canUndo, onEvent = onEvent) },
            end = {
                TextActionButton(
                    text = stringResource(R.string.card_more_options),
                    onClick = { showOptions = true },
                    trailingIcon = AppIcons.ChevronDown,
                )
            },
        )
    }

    if (showOptions) {
        NuancedAnswersSheet(
            productName = card.name,
            imageKey = card.imageKey,
            onAnswer = { answer ->
                showOptions = false
                onEvent(QuestionEvent.Nuanced(answer))
            },
            onDismiss = { showOptions = false },
        )
    }
}

@Composable
private fun ProductCardBody(
    card: ProductCardUi,
    banner: CardBannerSize?,
    popImage: Boolean,
    onBannerTop: (Dp) -> Unit,
    onPreset: (QuantityValue) -> Unit,
    onEvent: (QuestionEvent) -> Unit,
) {
    val density = LocalDensity.current
    var bodyCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPlaced { bodyCoordinates = it }
            .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 22.dp),
    ) {
        // The product and its context scroll when long (large font); the quantity controls stay at the bottom.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            val badge = QuestionTexts.cardBadge(card)
            if (card.categoryName != null || badge != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        card.categoryName?.let { Overline(text = it, heading = false) }
                    }
                    badge?.let {
                        Chip(text = stringResource(it.text), tone = if (it.watch) ChipTone.WATCH else ChipTone.POSITIVE)
                    }
                }
            }
            if (banner != null) {
                CardBanner(
                    imageKey = card.imageKey,
                    size = banner,
                    pop = popImage,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .onGloballyPositioned { coordinates ->
                            val body = bodyCoordinates ?: return@onGloballyPositioned
                            if (!body.isAttached || !coordinates.isAttached) return@onGloballyPositioned
                            onBannerTop(with(density) { body.localPositionOf(coordinates, Offset.Zero).y.toDp() })
                        },
                )
            }
            Text(
                text = card.name,
                style = CompagnonType.ProductTitle,
                color = CompagnonColors.Ink,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .semantics { heading() },
            )
            Text(
                text = stringResource(R.string.card_question, card.needPhrase),
                style = CompagnonType.Question,
                color = CompagnonColors.InkSoft,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            card.reason?.let { reason ->
                Hint(text = QuestionTexts.reason(reason).resolve(), modifier = Modifier.padding(top = 12.dp))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (card.units.size > 1) {
                UnitSelector(units = card.units, onSelect = { onEvent(QuestionEvent.SelectUnit(it)) })
            }

            val usual = stringResource(R.string.card_usual)
            val presetMatches = card.presets.any { it.value == card.quantity.selected.value }
            if (card.presets.isNotEmpty()) {
                val columns = presetColumns(card.presets.size)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    card.presets.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { preset ->
                                val selected = preset.value == card.quantity.selected.value
                                QuantityChoiceTile(
                                    value = preset.label,
                                    selected = selected,
                                    subline = if (selected && card.isSameAsLastTime) usual else null,
                                    contentDescription = stringResource(R.string.answer_yes_with_quantity, preset.label),
                                    onClick = { onPreset(preset.value) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            val label = card.quantity.selected.label
            FreeStepper(
                prefix = stringResource(R.string.card_other_quantity) + " ",
                value = if (card.isSameAsLastTime && !presetMatches) "$label · $usual" else label,
                itemName = card.name,
                canDecrement = card.quantity.canDecrement,
                canIncrement = card.quantity.canIncrement,
                onStep = { onEvent(QuestionEvent.StepQuantity(it)) },
            )
        }
    }
}

/** The leaves of the card banner (`Card.dc.html`), Basilic at 10 %. */
private val BannerLeaves = listOf(
    PatternLeaf(36f, 30f, -30f),
    PatternLeaf(270f, 78f, 40f, 0.8f),
    PatternLeaf(250f, 22f, -60f, 0.55f),
)

/**
 * The banner between the aisle and the product name: Basilic clair, radius 22, leaves as a watermark, a white glow,
 * and the product's image on its soft shadow.
 */
@Composable
private fun CardBanner(imageKey: String, size: CardBannerSize, pop: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(size.height)
            .clip(RoundedCornerShape(22.dp))
            .background(CompagnonColors.BasilLight)
            .drawBehind {
                val radius = 90.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0f)),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        LeafPattern(
            leaves = BannerLeaves,
            width = 306.dp,
            height = 112.dp,
            color = CompagnonColors.Basil.copy(alpha = 0.10f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .wrapContentSize(Alignment.TopStart, unbounded = true),
        )
        Illustration(key = imageKey, size = size.image, groundShadow = true, pop = pop, popDelayMillis = if (pop) 160 else 0)
    }
}

/**
 * The card behind: the next question's image in a banner placed like this card's ([bannerTop] in the card), so that
 * it is already in place when the card comes forward. A plain card without a banner (large font).
 */
@Composable
private fun NextCardPreview(imageKey: String, banner: CardBannerSize?, bannerTop: Dp?) {
    if (banner == null) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = bannerTop ?: 64.dp),
    ) {
        CardBanner(imageKey = imageKey, size = banner, pop = false)
    }
}

/** « Au poids | À la pièce »: Surface douce track, the selected unit on a white pill. */
@Composable
private fun UnitSelector(units: List<UnitOptionUi>, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CompagnonShapes.Control)
            .background(CompagnonColors.SurfaceSoft)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        units.forEach { option ->
            val shape = RoundedCornerShape(11.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .then(
                        if (option.selected) {
                            Modifier
                                .softShadow(shape, radius = 3.dp, offsetY = 1.dp, alpha = 0.12f)
                                .clip(shape)
                                .background(CompagnonColors.Surface)
                        } else {
                            Modifier.clip(shape)
                        },
                    )
                    .selectable(selected = option.selected, role = Role.RadioButton) { onSelect(option.unit) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.label,
                    style = CompagnonType.Secondary.copy(
                        fontWeight = if (option.selected) FontWeight.Bold else FontWeight.SemiBold,
                    ),
                    color = if (option.selected) CompagnonColors.Ink else CompagnonColors.InkSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** « ← glisser : non » / « glisser : oui → ». Visual only: TalkBack has the Oui / Non actions and the buttons. */
@Composable
private fun SwipeHints(modifier: Modifier = Modifier) {
    val style = CompagnonType.Caption.copy(fontWeight = FontWeight.SemiBold)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(AppIcons.ArrowBack, contentDescription = null, tint = CompagnonColors.InkSoft, modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.card_swipe_no), style = style, color = CompagnonColors.InkSoft)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.card_swipe_yes), style = style, color = CompagnonColors.InkSoft)
            Icon(AppIcons.ArrowForward, contentDescription = null, tint = CompagnonColors.InkSoft, modifier = Modifier.size(14.dp))
        }
    }
}

/** [Non] and [Oui] side by side (1 : 1.6), 60 high: the main answer on the right, under the thumb. */
@Composable
internal fun AnswerButtons(
    noText: String,
    yesText: String,
    onYes: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier,
    noIcon: ImageVector? = null,
    yesIcon: ImageVector? = null,
    yesTrailingIcon: ImageVector? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryButton(
            text = noText,
            onClick = onNo,
            leadingIcon = noIcon,
            minHeight = 60.dp,
            modifier = Modifier.weight(1f),
        )
        PrimaryButton(
            text = yesText,
            onClick = onYes,
            leadingIcon = yesIcon,
            trailingIcon = yesTrailingIcon,
            minHeight = 60.dp,
            modifier = Modifier.weight(1.6f),
        )
    }
}

/** The look of each nuanced answer: its tile tone and icon. */
private fun NuancedAnswer.tone(): OptionTone = when (this) {
    NuancedAnswer.LITTLE_LEFT, NuancedAnswer.ENOUGH_FOR_A_WEEK, NuancedAnswer.ENOUGH_FOR_A_MONTH -> OptionTone.STOCK
    NuancedAnswer.LATER, NuancedAnswer.NOT_THIS_TIME -> OptionTone.PAUSE
    NuancedAnswer.NEVER -> OptionTone.STOP
}

private fun NuancedAnswer.icon(): ImageVector = when (this) {
    NuancedAnswer.LITTLE_LEFT -> AppIcons.Levels
    NuancedAnswer.ENOUGH_FOR_A_WEEK -> AppIcons.Calendar
    NuancedAnswer.ENOUGH_FOR_A_MONTH -> AppIcons.CalendarMonth
    NuancedAnswer.LATER -> AppIcons.Clock
    NuancedAnswer.NOT_THIS_TIME -> AppIcons.Remove
    NuancedAnswer.NEVER -> AppIcons.Block
}

/**
 * « Plus d'options »: a Material bottom sheet with a handle, the product's image and name, a help sentence, [✕],
 * and the six nuanced answers in two groups (« Il m'en reste », « Pas maintenant »).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NuancedAnswersSheet(
    productName: String,
    imageKey: String,
    onAnswer: (NuancedAnswer) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val hideThen: (() -> Unit) -> Unit = { action ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = CompagnonShapes.Sheet,
        containerColor = CompagnonColors.Surface,
        contentColor = CompagnonColors.Ink,
        scrimColor = CompagnonColors.Scrim,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(CompagnonColors.LineStrong),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 0.dp, top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Illustration(
                    key = imageKey,
                    size = 36.dp,
                    plate = 52.dp,
                    popDelayMillis = 120,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = productName,
                        style = CompagnonType.SheetTitle,
                        color = CompagnonColors.Ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(text = stringResource(R.string.options_hint), style = CompagnonType.Secondary, color = CompagnonColors.InkSoft)
                }
                CompagnonIconButton(
                    icon = AppIcons.Close,
                    contentDescription = stringResource(R.string.action_close),
                    onClick = { hideThen(onDismiss) },
                    style = IconButtonStyle.SOFT,
                    iconSize = 18.dp,
                )
            }
            OptionGroup(
                title = stringResource(R.string.options_group_stock),
                answers = QuestionTexts.stockAnswers,
                onAnswer = { answer -> hideThen { onAnswer(answer) } },
            )
            OptionGroup(
                title = stringResource(R.string.options_group_not_now),
                answers = QuestionTexts.notNowAnswers,
                onAnswer = { answer -> hideThen { onAnswer(answer) } },
            )
        }
    }
}

@Composable
private fun OptionGroup(title: String, answers: List<NuancedAnswer>, onAnswer: (NuancedAnswer) -> Unit) {
    Overline(text = title, modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 6.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CompagnonShapes.Block)
            .background(CompagnonColors.Background),
    ) {
        answers.forEachIndexed { index, answer ->
            if (index > 0) BlockDivider()
            ResponseOption(
                label = stringResource(QuestionTexts.nuancedLabel(answer)),
                hint = stringResource(QuestionTexts.nuancedHint(answer)),
                icon = answer.icon(),
                tone = answer.tone(),
                onClick = { onAnswer(answer) },
            )
        }
    }
}
