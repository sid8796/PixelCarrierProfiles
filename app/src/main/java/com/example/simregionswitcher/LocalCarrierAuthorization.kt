package com.example.simregionswitcher

/** Only an explicit confirmation in this running app can arm its instrumentation. */
object LocalCarrierAuthorization {
    private var id: String? = null
    private var plan: RegionPlan? = null
    @Synchronized fun arm(request: String, selected: RegionPlan) { id = request; plan = selected }
    @Synchronized fun consume(request: String, selected: RegionPlan): Boolean {
        if (id != request || plan != selected) return false
        id = null; plan = null; return true
    }
}
