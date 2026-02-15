package com.kormax.felicatool.util

import com.kormax.felicatool.felica.Area
import com.kormax.felicatool.felica.Service
import com.kormax.felicatool.felica.ServiceType
import com.kormax.felicatool.service.SystemScanContext

/**
 * Groups services by their service number (upper 10 bits) AND service type. Services with the same
 * number and type share the same block data and are conceptually "grouped".
 */
object ServiceGrouper {

    /**
     * Represents a group of services that share the same service number and type. All services in a
     * group have the same block data.
     */
    data class ServiceGroup(
        /** The common service number (0-1023) shared by all services in this group */
        val number: Int,
        /** The common service type (RANDOM, CYCLIC, PURSE) shared by all services in this group */
        val type: ServiceType,
        /** All services with this service number and type, sorted by attribute */
        val services: List<Service>,
        /** The parent area containing these services (null for system-level services) */
        val parentArea: Area? = null,
    ) {
        /** Returns true if this group contains multiple services */
        val isMultiService: Boolean = services.size > 1

        /** Returns the primary service (typically the one with the most permissive access) */
        val primaryService: Service
            get() =
                services.firstOrNull { !it.attribute.authenticationRequired } ?: services.first()

        /** Returns services that require authentication */
        val authenticatedServices: List<Service>
            get() = services.filter { it.attribute.authenticationRequired }

        /** Returns services that don't require authentication */
        val unauthenticatedServices: List<Service>
            get() = services.filter { !it.attribute.authenticationRequired }

        /** Returns all unique access modes present in this group */
        val accessModes: Set<String>
            get() =
                services
                    .map { service ->
                        buildString {
                            if (service.attribute.authenticationRequired) append("AUTH ")
                            else append("FREE ")
                            append(service.attribute.mode.name)
                        }
                    }
                    .toSet()
    }

    /** Key for grouping services by number, type, and parent area. */
    private data class GroupKey(
        val number: Int,
        val type: ServiceType,
        val parentArea: Area? = null,
    )

    /** Finds the most immediate containing area for a service. */
    private fun findContainingArea(service: Service, context: SystemScanContext): Area? {
        return context.nodes
            .filterIsInstance<Area>()
            .filter { candidate -> service.belongsTo(candidate) }
            .minByOrNull { it.endNumber - it.number }
    }

    /**
     * Groups services from a system scan context by their service number, type, and containing
     * area. Services are only grouped together if they belong to the same parent area.
     *
     * @param context The system scan context containing services
     * @return List of service groups, sorted by service number then type
     */
    fun groupServices(context: SystemScanContext): List<ServiceGroup> {
        val services = context.nodes.filterIsInstance<Service>()

        // Map each service to its containing area
        val serviceToArea = services.associateWith { findContainingArea(it, context) }

        return services
            .groupBy { service ->
                GroupKey(service.number, service.attribute.type, serviceToArea[service])
            }
            .map { (key, servicesInGroup) ->
                ServiceGroup(
                    number = key.number,
                    type = key.type,
                    services =
                        servicesInGroup.sortedWith(
                            compareBy(
                                // Prefer unauthenticated services first
                                { it.attribute.authenticationRequired },
                                // Then by mode (RW before RO)
                                { it.attribute.mode.ordinal },
                                // Finally by attribute value for consistency
                                { it.attribute.value },
                            )
                        ),
                    parentArea = key.parentArea,
                )
            }
            .sortedWith(
                compareBy(
                    // Sort by parent area number first (null/root areas come first)
                    { it.parentArea?.number ?: -1 },
                    { it.number },
                    { it.type.ordinal },
                )
            )
    }

    /**
     * Groups a list of services by their service number and type only. Note: This method does NOT
     * consider parent areas. Use groupServices(context) for proper hierarchical grouping.
     *
     * @param services List of services to group
     * @return List of service groups, sorted by service number then type
     */
    fun groupServices(services: List<Service>): List<ServiceGroup> {
        return services
            .groupBy { GroupKey(it.number, it.attribute.type, null) }
            .map { (key, servicesInGroup) ->
                ServiceGroup(
                    number = key.number,
                    type = key.type,
                    services =
                        servicesInGroup.sortedWith(
                            compareBy(
                                // Prefer unauthenticated services first
                                { it.attribute.authenticationRequired },
                                // Then by mode (RW before RO)
                                { it.attribute.mode.ordinal },
                                // Finally by attribute value for consistency
                                { it.attribute.value },
                            )
                        ),
                    parentArea = null,
                )
            }
            .sortedWith(compareBy({ it.number }, { it.type.ordinal }))
    }

    /** Statistics about service grouping for a scan context. */
    data class GroupingStatistics(
        val totalServices: Int,
        val totalGroups: Int,
        val singleServiceGroups: Int,
        val multiServiceGroups: Int,
        val largestGroupSize: Int,
    )

    /** Calculates grouping statistics for a system scan context. */
    fun calculateStatistics(context: SystemScanContext): GroupingStatistics {
        val groups = groupServices(context)
        return GroupingStatistics(
            totalServices = groups.sumOf { it.services.size },
            totalGroups = groups.size,
            singleServiceGroups = groups.count { !it.isMultiService },
            multiServiceGroups = groups.count { it.isMultiService },
            largestGroupSize = groups.maxOfOrNull { it.services.size } ?: 0,
        )
    }
}
