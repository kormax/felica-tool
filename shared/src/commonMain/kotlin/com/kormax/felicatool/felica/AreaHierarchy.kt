package com.kormax.felicatool.felica

internal val areaContainmentComparator: Comparator<Area> =
    compareBy<Area> { it.endNumber - it.number }
        .thenByDescending { it.number }
        .thenBy { it.attribute.canCreateSubArea }
        .thenBy { it.fullCode.toHexString() }

internal fun Area.hasSameRangeButCannotNestIn(candidate: Area): Boolean =
    number == candidate.number &&
        endNumber == candidate.endNumber &&
        attribute.canCreateSubArea &&
        !candidate.attribute.canCreateSubArea

internal fun Node.areaPath(areas: List<Area>): List<Area> {
    val leafToRoot = mutableListOf<Area>()
    val visited = mutableSetOf<Area>()
    var child: Node = this
    while (true) {
        val childArea = child as? Area
        val parent =
            areas
                .filter { candidate ->
                    candidate !in visited &&
                        child.belongsTo(candidate) &&
                        (childArea == null ||
                            (candidate != childArea &&
                                !childArea.hasSameRangeButCannotNestIn(candidate)))
                }
                .minWithOrNull(areaContainmentComparator) ?: return emptyList()
        leafToRoot += parent
        if (parent.isRoot) return leafToRoot.asReversed()
        visited += parent
        child = parent
    }
}
