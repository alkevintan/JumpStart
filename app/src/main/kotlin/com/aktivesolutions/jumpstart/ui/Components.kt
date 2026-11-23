package com.aktivesolutions.jumpstart.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aktivesolutions.jumpstart.data.AppRepository
import com.aktivesolutions.jumpstart.search.Item
import com.aktivesolutions.jumpstart.search.Kind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Text with the mockup's explicit size / line-height / tracking triples. */
@Composable
fun T(
    text: String,
    size: Int,
    line: Int,
    color: Color,
    weight: Int = 400,
    spacing: Float = 0f,
    modifier: Modifier = Modifier,
    italic: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) = Text(
    text = text, modifier = modifier, color = color, fontSize = size.sp, lineHeight = line.sp,
    fontWeight = FontWeight(weight), letterSpacing = spacing.sp,
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    maxLines = maxLines, overflow = TextOverflow.Ellipsis,
)

@Composable
fun SymbolIcon(icon: ImageVector, size: Dp = 24.dp, tint: Color, modifier: Modifier = Modifier) =
    Icon(icon, contentDescription = null, modifier = modifier.size(size), tint = tint)

fun Modifier.tap(onClick: () -> Unit, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp)) =
    clip(shape).clickable(role = Role.Button, onClick = onClick)

/** Tap = [onClick]; long-press (haptic) = [onLongClick], announced to accessibility as "Edit tags". Plain tap when [onLongClick] is null. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tapOrHold(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
): Modifier {
    val haptic = LocalHapticFeedback.current
    return clip(shape).then(
        if (onLongClick == null) Modifier.clickable(role = Role.Button, onClick = onClick)
        else Modifier.combinedClickable(
            role = Role.Button,
            onLongClickLabel = "Edit tags",
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            },
            onClick = onClick,
        ),
    )
}

fun initials(n: String): String =
    n.split(" ").filter { it.isNotEmpty() }.take(2).map { it.first() }.joinToString("").uppercase()

/** Circular avatar for an item: themed letter, real icon (apps with themed off), initials (contacts), glyph (settings). */
@Composable
fun ItemAvatar(item: Item, size: Dp, fontSp: Int, themed: Boolean, apps: AppRepository) {
    val c = Js.c
    val (bg, fg) = when (item.kind) {
        Kind.APP -> c.pc to c.opc
        Kind.CONTACT -> c.sec to c.osec
        Kind.SETTING -> c.sch4 to c.onv
    }
    val letter = if (item.kind == Kind.CONTACT) initials(item.title) else item.title.take(1).uppercase()
    if (item.kind == Kind.APP && !themed) {
        val px = with(LocalDensity.current) { size.roundToPx() }
        // State is created per (component key, px): when this slot is reused for a different app (top hit,
        // list rows without keys) the old bitmap is dropped at once (letter placeholder while loading) and the
        // effect for the old key is cancelled, so a stale load can never write into the new key's state.
        val bmpState = remember(item.key, px) { mutableStateOf(apps.cachedIcon(item.key, px)) }
        LaunchedEffect(item.key, px) {
            if (bmpState.value == null) bmpState.value = withContext(Dispatchers.IO) { apps.loadIcon(item.key, px) }
        }
        val b = bmpState.value
        if (b != null) {
            Image(b, contentDescription = null, modifier = Modifier.size(size))
            return
        }
    }
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        T(letter, fontSp, fontSp + 4, fg, 500)
    }
}

@Composable
fun PillButton(label: String, bg: Color, fg: Color, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(bg).clickable(role = Role.Button, onClick = onClick)
            .padding(start = if (icon != null) 16.dp else 24.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) SymbolIcon(icon, 18.dp, fg)
        T(label, 14, 20, fg, 500, 0.1f)
    }
}

@Composable
fun TextActionButton(label: String, color: Color, icon: ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        T(label, 14, 20, color, 500, 0.1f)
        if (icon != null) SymbolIcon(icon, 18.dp, color)
    }
}

@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) =
    T(text, 14, 20, Js.c.primary, 500, 0.1f, modifier)

@Composable
fun SnackToast(msg: String, modifier: Modifier = Modifier) {
    val c = Js.c
    Box(
        modifier.fillMaxWidth().padding(horizontal = 12.dp).defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(4.dp)).background(c.on).padding(horizontal = 16.dp, vertical = 14.dp),
    ) { T(msg, 14, 20, c.inverseOn, 400, 0.25f) }
}
