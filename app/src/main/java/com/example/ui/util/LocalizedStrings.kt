package com.example.ui.util

object LocalizedStrings {
    private val translations = mapOf(
        "English (US)" to mapOf(
            "home" to "Home",
            "explore" to "Explore",
            "create" to "Create",
            "reels" to "Reels",
            "profile" to "Profile",
            "notifications" to "Notifications",
            "messages" to "Messages",
            "settings_privacy" to "Settings & Privacy",
            "posts" to "Posts",
            "followers" to "Followers",
            "following" to "Following",
            "edit_profile" to "Edit Profile",
            "logout" to "Log Out",
            "preferences" to "Preferences & Display",
            "dark_theme" to "Dark Theme",
            "app_language" to "App Language",
            "privacy" to "Account Privacy & Visibility",
            "private_account" to "Private Account",
            "search_placeholder" to "Search users, tags...",
            "add_post" to "New Post",
            "add_story" to "Your Story"
        ),
        "Hindi (हिंदी)" to mapOf(
            "home" to "होम",
            "explore" to "एक्सप्लोर",
            "create" to "बनाएं",
            "reels" to "रील्स",
            "profile" to "प्रोफ़ाइल",
            "notifications" to "सूचनाएं",
            "messages" to "संदेश",
            "settings_privacy" to "सेटिंग्स और गोपनीयता",
            "posts" to "पोस्ट",
            "followers" to "फ़ॉलोअर्स",
            "following" to "फ़ॉलोइंग",
            "edit_profile" to "प्रोफ़ाइल संपादित करें",
            "logout" to "लॉग आउट",
            "preferences" to "प्राथमिकताएं और डिस्प्ले",
            "dark_theme" to "डार्क थीम",
            "app_language" to "ऐप भाषा",
            "privacy" to "खाता गोपनीयता",
            "private_account" to "प्राइवेट अकाउंट",
            "search_placeholder" to "खोजें...",
            "add_post" to "नया पोस्ट",
            "add_story" to "आपकी स्टोरी"
        ),
        "Hinglish" to mapOf(
            "home" to "Home",
            "explore" to "Explore Karo",
            "create" to "Create Karo",
            "reels" to "Reels",
            "profile" to "Profile",
            "notifications" to "Notifications",
            "messages" to "Messages",
            "settings_privacy" to "Settings aur Privacy",
            "posts" to "Posts",
            "followers" to "Followers",
            "following" to "Following",
            "edit_profile" to "Profile Edit Karo",
            "logout" to "Log Out Karo",
            "preferences" to "Preferences aur Display",
            "dark_theme" to "Dark Theme",
            "app_language" to "App Language",
            "privacy" to "Account Privacy",
            "private_account" to "Private Account",
            "search_placeholder" to "Search karo...",
            "add_post" to "Naya Post",
            "add_story" to "Aapki Story"
        ),
        "Spanish (Español)" to mapOf(
            "home" to "Inicio",
            "explore" to "Explorar",
            "create" to "Crear",
            "reels" to "Reels",
            "profile" to "Perfil",
            "notifications" to "Notificaciones",
            "messages" to "Mensajes",
            "settings_privacy" to "Configuración y Privacidad",
            "posts" to "Publicaciones",
            "followers" to "Seguidores",
            "following" to "Siguiendo",
            "edit_profile" to "Editar Perfil",
            "logout" to "Cerrar Sesión",
            "preferences" to "Preferencias y Pantalla",
            "dark_theme" to "Tema Oscuro",
            "app_language" to "Idioma de la App",
            "privacy" to "Privacidad de la Cuenta",
            "private_account" to "Cuenta Privada",
            "search_placeholder" to "Buscar usuarios...",
            "add_post" to "Nueva Publicación",
            "add_story" to "Tu Historia"
        )
    )

    fun get(key: String, language: String): String {
        val langMap = translations[language] ?: translations["English (US)"]!!
        return langMap[key] ?: translations["English (US)"]!![key] ?: key
    }
}
