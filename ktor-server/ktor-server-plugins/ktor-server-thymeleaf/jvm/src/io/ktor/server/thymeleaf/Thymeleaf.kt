/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.thymeleaf

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import org.thymeleaf.*
import org.thymeleaf.context.*
import java.io.*
import java.util.*

/**
 * Represents a content handled by [Thymeleaf] plugin.
 *
 * @param template name that is resolved by thymeleaf
 * @param model to be passed during template rendering
 * @param etag value for `E-Tag` header (optional)
 * @param contentType of response (optional, `text/html` with UTF-8 character encoding by default)
 * @param locale object represents a specific geographical, political, or cultural region
 * @param fragments names from the [template] that is resolved by thymeleaf
 */
public class ThymeleafContent(
    public val template: String,
    public val model: Map<String, Any>,
    public val etag: String? = null,
    public val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8),
    public val locale: Locale = Locale.getDefault(),
    public val fragments: Set<String> = setOf()
)

/**
 * A plugin that allows you to use Thymeleaf templates as views within your application.
 * Provides the ability to respond with [Thymeleaf]
 */
public class Thymeleaf private constructor(private val engine: TemplateEngine) {
    /**
     * A plugin installing companion object
     */
    public companion object Plugin : ApplicationPlugin<ApplicationCallPipeline, TemplateEngine, Thymeleaf> {
        override val key: AttributeKey<Thymeleaf> = AttributeKey<Thymeleaf>("thymeleaf")

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: TemplateEngine.() -> Unit
        ): Thymeleaf {
            val config = TemplateEngine().apply(configure)
            val plugin = Thymeleaf(config)
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Transform) { value ->
                if (value is ThymeleafContent) {
                    val response = plugin.process(value)
                    proceedWith(response)
                }
            }
            return plugin
        }
    }

    private fun process(content: ThymeleafContent): OutgoingContent = with(content) {
        val context = Context(locale).apply { setVariables(model) }

        val result = TextContent(
            text = engine.process(template, fragments, context),
            contentType
        )
        if (etag != null) {
            result.versions += EntityTagVersion(etag)
        }
        return result
    }
}