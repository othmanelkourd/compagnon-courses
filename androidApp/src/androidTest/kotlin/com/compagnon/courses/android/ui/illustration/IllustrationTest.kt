package com.compagnon.courses.android.ui.illustration

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.compagnon.courses.android.ui.components.SuggestionRow
import com.compagnon.courses.android.ui.theme.CompagnonTheme
import com.compagnon.courses.core.model.illustration.Illustrations
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The 3D images are decorative: TalkBack never reads them, the text next to them says what they are. */
@RunWith(AndroidJUnit4::class)
class IllustrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val described = SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)
    private val image = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image)

    @Test
    fun anImageHasNoDescriptionNorImageRole() {
        composeRule.setContent {
            CompagnonTheme {
                Column {
                    Illustration(key = "tomato", size = 84.dp)
                    Illustration(key = "glass_of_milk", size = 26.dp, plate = 36.dp, dimmed = true)
                    // An unknown key shows the fallback, decorative too.
                    Illustration(key = "not_an_illustration", size = 40.dp)
                    HeroIllustration(
                        key = Illustrations.Screen.SESSION_DONE,
                        disc = 160.dp,
                        image = 108.dp,
                        ring = 12.dp,
                        badge = HeroBadge(size = 44.dp, border = 4.dp, tick = 22.dp, offset = DpOffset((-4).dp, (-6).dp)),
                    )
                    LeafPattern(leaves = listOf(PatternLeaf(20f, 20f, 30f)), width = 40.dp, height = 40.dp, color = Color.Black)
                }
            }
        }

        composeRule.onAllNodes(described, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodes(image, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun aSuggestionLineIsReadByItsNameNotItsImage() {
        composeRule.setContent {
            CompagnonTheme {
                SuggestionRow(name = "Baguette", reason = null, imageKey = "baguette_bread", checked = false, onToggle = {}) {}
            }
        }

        composeRule.onAllNodes(described, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Baguette").assertCountEquals(1)
    }
}
