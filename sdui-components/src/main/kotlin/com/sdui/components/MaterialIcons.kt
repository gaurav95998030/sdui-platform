package com.sdui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import com.sdui.core.registry.Registry

/**
 * Name-to-vector table for the `icon` component.
 *
 * Explicit rather than reflective on purpose: R8 can see exactly which icons are
 * reachable and strip the rest, and a screen cannot reach arbitrary classes by
 * name. Hosts extend it with [register] for brand or domain glyphs.
 */
public object MaterialIcons {

    private val registry = Registry<ImageVector>("IconRegistry").apply {
        registerAll(
            mapOf(
                "add" to Icons.Filled.Add,
                "arrowBack" to Icons.Filled.ArrowBack,
                "arrowForward" to Icons.Filled.ArrowForward,
                "calendar" to Icons.Filled.CalendarToday,
                "cancel" to Icons.Filled.Cancel,
                "camera" to Icons.Filled.PhotoCamera,
                "check" to Icons.Filled.Check,
                "checkCircle" to Icons.Filled.CheckCircle,
                "close" to Icons.Filled.Close,
                "creditCard" to Icons.Filled.CreditCard,
                "delete" to Icons.Filled.Delete,
                "download" to Icons.Filled.Download,
                "edit" to Icons.Filled.Edit,
                "email" to Icons.Filled.Email,
                "favorite" to Icons.Filled.Favorite,
                "filter" to Icons.Filled.FilterList,
                "home" to Icons.Filled.Home,
                "info" to Icons.Filled.Info,
                "chevronDown" to Icons.Filled.KeyboardArrowDown,
                "chevronUp" to Icons.Filled.KeyboardArrowUp,
                "chevronLeft" to Icons.Filled.KeyboardArrowLeft,
                "chevronRight" to Icons.Filled.KeyboardArrowRight,
                "location" to Icons.Filled.LocationOn,
                "lock" to Icons.Filled.Lock,
                "logout" to Icons.Filled.Logout,
                "menu" to Icons.Filled.Menu,
                "moreHorizontal" to Icons.Filled.MoreHoriz,
                "moreVertical" to Icons.Filled.MoreVert,
                "notifications" to Icons.Filled.Notifications,
                "person" to Icons.Filled.Person,
                "phone" to Icons.Filled.Phone,
                "play" to Icons.Filled.PlayArrow,
                "qrCode" to Icons.Filled.QrCodeScanner,
                "refresh" to Icons.Filled.Refresh,
                "remove" to Icons.Filled.Remove,
                "search" to Icons.Filled.Search,
                "send" to Icons.Filled.Send,
                "settings" to Icons.Filled.Settings,
                "share" to Icons.Filled.Share,
                "cart" to Icons.Filled.ShoppingCart,
                "star" to Icons.Filled.Star,
                "starOutline" to Icons.Filled.StarBorder,
                "visible" to Icons.Filled.Visibility,
                "hidden" to Icons.Filled.VisibilityOff,
                "warning" to Icons.Filled.Warning,
            ),
        )
        alias("back", "arrowBack")
        alias("forward", "arrowForward")
        alias("expandMore", "chevronDown")
        alias("expandLess", "chevronUp")
        alias("profile", "person")
        alias("scanQR", "qrCode")
        alias("eye", "visible")
        alias("eyeOff", "hidden")
    }

    public fun resolve(name: String): ImageVector? = registry.get(name)

    public fun register(name: String, icon: ImageVector, override: Boolean = true) {
        registry.register(name, icon, override)
    }

    public val names: Set<String> get() = registry.keys
}
