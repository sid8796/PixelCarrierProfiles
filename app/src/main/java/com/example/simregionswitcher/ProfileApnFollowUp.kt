package com.example.simregionswitcher

/** A follow-up belongs to one explicitly confirmed request in this app process. Never replay it. */
class ProfileApnFollowUp<T> {
    private var id: String?=null
    private var plan: RegionPlan?=null
    private var payload: T?=null
    @Synchronized fun arm(request: String, selected: RegionPlan, snapshot: T) {
        require(request.isNotBlank() && selected.valid() && !selected.reset && !selected.configOnly)
        id=request; plan=selected; payload=snapshot
    }
    @Synchronized fun take(request: String, selected: RegionPlan): T? {
        if(id!=request || plan!=selected) return null
        val result=payload; id=null; plan=null; payload=null
        return result
    }
    @Synchronized fun clear() { id=null; plan=null; payload=null }
}
