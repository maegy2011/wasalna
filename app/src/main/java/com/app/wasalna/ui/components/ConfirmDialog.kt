package com.app.wasalna.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * رسائل التأكيد قبل أي إجراء (confirmation gate).
 *
 * RULE — متى تظهر رسالة تأكيد ومتى لا:
 * - تظهر فقط قبل إجراء مُتلِف أو صعب التراجع عنه (فقدان بيانات / مجهود
 *   إعادة التنزيل): حذف مكان محفوظ، حذف خريطة أوفلاين محمّلة، إلغاء تنزيل
 *   جارٍ، مسح نقطة البيت/الشغل.
 * - لا تظهر أبدًا لإجراءات يومية غير مُتلِفة: التنقل بين الشاشات، مفاتيح
 *   التبديل (switches)، الزوم، اختيار وسيلة المواصلات، المشاركة — الـ nag
 *   في هذه المواضع مرفوض.
 *
 * Dismiss (تراجع / خارج الحوار) = الوضع الآمن دائمًا: لا يحدث شيء.
 * زر التأكيد يحمل فعلًا عربيًا واضحًا حسب الإجراء (احذف / ألغِ / امسح)،
 * وبلون الخطر للأفعال المُتلِفة، وبلون primary لغيرها.
 *
 * Material3 AlertDialog — يدعم RTL تلقائيًا حسب لغة النظام (العربية)،
 * كل الأزرار ≥ 48dp، وتسميات وصول (contentDescription) عربية.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String = "تراجع",
    isDestructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "تأكيد: $confirmLabel" },
                colors = if (isDestructive) {
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    )
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "تراجع عن: $title" },
            ) {
                Text(dismissLabel)
            }
        },
    )
}
