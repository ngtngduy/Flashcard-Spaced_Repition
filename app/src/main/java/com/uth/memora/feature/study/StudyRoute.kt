package com.uth.memora.feature.study

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.uth.memora.core.model.StudyMode
import com.uth.memora.feature.common.PlaceholderScreen

@Composable
fun StudyRoute(
    deckId: String,
    studyMode: StudyMode = StudyMode.DUE_REVIEW,
    onIntermission: (String) -> Unit,
    onFinishSession: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlaceholderScreen(
        title = "Phiên học (${studyMode.labelVi})",
        owner = "Người 2 – UI & Luồng học",
        description = "Figma 2104:23/2101:14 (Học từ mới 2 nút) & 2101:15/2101:16 (Ôn tập 4 nút SM-2) + Hoàn tác 5s ($deckId).",
        modifier = modifier
    )
}
