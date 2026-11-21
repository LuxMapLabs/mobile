package com.luxmap.core.common

fun communeLabel(
    id: String,
    name: String?,
): String = name?.takeIf { it.isNotBlank() } ?: id
