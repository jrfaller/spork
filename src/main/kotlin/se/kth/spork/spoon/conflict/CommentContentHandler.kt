package se.kth.spork.spoon.conflict

import se.kth.spork.util.lineBasedMerge
import spoon.reflect.declaration.CtElement
import spoon.reflect.path.CtRole

/**
 * A conflict handler for comment contents.
 *
 * @author Simon Larsén
 */
class CommentContentHandler : ContentConflictHandler {
    override val role: CtRole
        get() = CtRole.COMMENT_CONTENT

    override fun handleConflict(
        baseVal: Any?,
        leftVal: Any,
        rightVal: Any,
        baseElem: CtElement?,
        leftElem: CtElement,
        rightElem: CtElement,
        diff3: Boolean,
    ): Pair<Any?, Boolean> {
        return Pair(mergeComments(baseVal ?: "", leftVal, rightVal, diff3), false)
    }

    private fun mergeComments(base: Any, left: Any, right: Any, diff3: Boolean): Any? {
        val merge = lineBasedMerge(base.toString(), left.toString(), right.toString(), diff3)
        return if (merge.second > 0) {
            null
        } else {
            merge.first
        }
    }
}
