package com.example.obsidianandroidwidgets

import android.content.Context

internal object SampleNote {
    const val IMAGE_CREDIT = "https://unsplash.com/photos/girl-holding-two-eggs-while-putting-it-on-her-eyes-ZBkH8G4_yyE"
    fun markdown(context: Context): String {
        val image = context.assets.open("sample-eggs.jpg.b64").bufferedReader().use { it.readText() }.trim()
        return """
            ### Friday
            - ==Important:== Buy more eggs
            - **Eggs to buy:**
              - Buy ~~two~~ *twelve* eggs
            - Buy [these](https://example.com/) eggs
            - Buy $\displaystyle \frac{1}{2\pi i}\oint_{|z|=1}\frac{12e^z}{z}\,dz$ eggs

            ```kotlin
            println("Buy eggs today!")
            ```

            > A box without hinges, key, or lid,
            > Yet golden treasure inside is hid.
            ${'\u2014'} J.R.R. Tolkien

            ___
            - [X] Bought eggs
            - [ ] Log eggs in #Eggs/2026

            ![Girl holding two eggs](data:image/jpeg;base64,$image)
            > *Image source: [(x)]($IMAGE_CREDIT)*
        """.trimIndent()
    }
}
