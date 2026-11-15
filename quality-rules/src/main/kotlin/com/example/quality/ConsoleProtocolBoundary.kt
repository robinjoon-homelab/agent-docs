package com.example.quality

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

class ConsoleProtocolBoundary(
    config: Config,
) : Rule(config, "Only the CLI console protocol may print lines or terminate the process."),
    RequiresAnalysisApi {
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        check(expression, allowProtocol = true)
    }

    override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression) {
        super.visitCallableReferenceExpression(expression)
        check(expression, allowProtocol = false)
    }

    private fun check(
        expression: KtExpression,
        allowProtocol: Boolean,
    ) {
        analyze(expression) {
            val call = expression.resolveToCall()?.successfulFunctionCallOrNull() ?: return
            val symbols = sequenceOf(call.symbol) + call.symbol.allOverriddenSymbols
            val forbidden = symbols.any { it.callableId?.asSingleFqName()?.asString() in forbiddenCalls }
            if (forbidden &&
                !(
                    allowProtocol &&
                        permitted(
                            expression,
                            call.symbol.callableId
                                ?.asSingleFqName()
                                ?.asString(),
                        )
                )
            ) {
                report(Finding(Entity.from(expression), "Use the application logger or the CLI Console protocol adapter."))
            }
        }
    }

    private fun KaSession.permitted(
        expression: KtExpression,
        target: String?,
    ): Boolean {
        val ancestors = generateSequence(expression.parent) { it.parent }
        val scope = ancestors.firstOrNull { it is KtNamedFunction || it is KtLambdaExpression }
        val function = scope as? KtNamedFunction ?: return false
        if (function.parent !is KtClassBody) return false
        val symbol = function.symbol
        val id = symbol.callableId ?: return false
        if (id.classId?.asSingleFqName()?.asString() != CONSOLE_CLASS || symbol.receiverParameter != null) return false
        val parameter = symbol.valueParameters.singleOrNull() ?: return false
        val type = (parameter.returnType as? KaClassType)?.classId?.asSingleFqName()?.asString()
        if (parameter.isVararg || parameter.returnType.nullability != KaTypeNullability.NON_NULLABLE) return false
        if (function.typeParameters.isNotEmpty()) return false
        if (target == "kotlin.system.exitProcess") return function.name == "finish" && type == "kotlin.Int"
        if (type != "kotlin.String") return false
        val stream =
            when (function.name) {
                "output" -> "java.lang.System.out"
                "error", "instruction" -> "java.lang.System.err"
                else -> return false
            }
        val qualified = expression.parent as? KtDotQualifiedExpression ?: return false
        if (qualified.selectorExpression != expression) return false
        val receiver =
            qualified.receiverExpression
                .resolveToCall()
                ?.successfulVariableAccessCall()
                ?.symbol
        return receiver?.callableId?.asSingleFqName()?.asString() == stream
    }

    private companion object {
        const val CONSOLE_CLASS = "xyz.robinjoon.adocs.adapter.inbound.console.Console"
        val forbiddenCalls = setOf("java.io.PrintStream.println", "kotlin.system.exitProcess")
    }
}
