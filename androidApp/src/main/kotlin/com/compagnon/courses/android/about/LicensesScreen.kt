package com.compagnon.courses.android.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.components.BlockDivider
import com.compagnon.courses.android.ui.components.SectionBlock
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonType
import com.compagnon.courses.feature.about.LicenseGroup
import com.compagnon.courses.feature.about.NoticePlatform
import com.compagnon.courses.feature.about.OpenSourceNotices
import com.compagnon.courses.feature.about.groupsFor

/** « Licences open source »: the components of the Android app, grouped by license, each with its full text. */
@Composable
internal fun LicensesScreen(
    onBack: () -> Unit,
    groups: List<LicenseGroup> = OpenSourceNotices.groupsFor(NoticePlatform.ANDROID),
) {
    AboutScaffold(title = stringResource(R.string.licenses_title), onBack = onBack) {
        Text(
            text = stringResource(R.string.licenses_intro),
            style = CompagnonType.BodyCompact,
            color = CompagnonColors.InkSoft,
        )
        groups.forEach { group -> LicenseBlock(group) }
    }
}

@Composable
private fun LicenseBlock(group: LicenseGroup) {
    var expanded by rememberSaveable(group.license.id) { mutableStateOf(false) }
    SectionBlock(title = group.license.title) {
        group.components.forEach { component ->
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(component.name, style = CompagnonType.ListRow, color = CompagnonColors.Ink)
                Text(component.owner, style = CompagnonType.Help, color = CompagnonColors.InkSoft)
            }
            BlockDivider()
        }
        Text(
            text = stringResource(if (expanded) R.string.licenses_hide_text else R.string.licenses_show_text),
            style = CompagnonType.ButtonText,
            color = CompagnonColors.Basil,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                BlockDivider()
                Text(
                    text = group.license.text,
                    style = CompagnonType.Caption,
                    color = CompagnonColors.Ink,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
