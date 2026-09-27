package com.sagewire.rangewater.ui.help

data class FieldGuideSection(
    val title: String,
    val body: String
)

object RangeWaterHelpContent {
    const val WELCOME_TITLE = "Welcome to RangeWater. Thanks for using it."
    const val WELCOME_BODY =
        "Map water access, pastures, gates, herds, grazing circuits, rest, and forage—all while keeping your ranch records on your device."

    val fieldTips = listOf(
        "Assign water sources to pastures to see accessible coverage.",
        "Create a portable backup before making major boundary changes.",
        "Grazing Circuits organize pastures but do not move a herd.",
        "Recorded rest is calculated from completed herd movements.",
        "Forage estimates are only as accurate as the samples entered.",
        "Press and hold during placement to use the precision loupe.",
        "Green shows preferred water access through 800 feet.",
        "Yellow shows the 800-to-1,000-foot planning transition.",
        "Map acreage is an estimate, not a surveyed boundary.",
        "A paddock split is a plan until the polywire is installed in the field."
    )

    val contextualHints = linkedMapOf(
        "draw_pasture" to "Tap corners in order. Use Undo for mistakes and Finish to close it.",
        "edit_boundary" to "Select a corner to move it, or use Add Corner on a fence segment.",
        "precision_loupe" to "Press and hold, then move the map point beneath the crosshair.",
        "add_gate" to "Place the gate directly on a fence segment connected to this pasture.",
        "water_assignment" to "Accessible coverage includes only explicitly assigned pastures.",
        "herd_movement" to "Planning does not move the herd. Complete the movement when it occurs.",
        "grazing_circuit" to "A circuit is a planning group. Assigning it does not move the herd.",
        "record_forage" to "Measure several representative undisturbed points before entering an average.",
        "paddock_split" to "This plans temporary polywire and does not replace the pasture boundary.",
        "data_safety" to "Backups stored outside RangeWater can restore records after replacement or loss."
    )

    val fieldGuide = listOf(
        FieldGuideSection(
            "Getting Started",
            "Move and zoom the map to your ranch. Draw the outside boundary of each pasture, add water sources and gates, then create herds and record movements. RangeWater saves ranch records locally for offline field use."
        ),
        FieldGuideSection(
            "Reading the Map and Imagery",
            "Aerial switches between USGS overview and USDA NAIP detail imagery. Labeled provides a navigation map. Changing modes preserves the map position. Provider attribution remains visible at the bottom of the map."
        ),
        FieldGuideSection(
            "Drawing and Correcting Pastures",
            "Choose Draw Pasture and place fence corners in order. Finish requires at least three different corners and rejects crossed boundaries. Edit Boundary can move, add, or remove corners. Mapped acreage is an estimate, not a surveyed boundary."
        ),
        FieldGuideSection(
            "Precision Placement and Snapping",
            "Press and hold during a supported placement to see the magnified crosshair. Move until the desired fence post, line, or location sits beneath the crosshair, then release. Snapping can join an existing shared corner or project a point onto a fence segment."
        ),
        FieldGuideSection(
            "Planning a Paddock Split",
            "Select a pasture and choose Plan Paddock Split. Place two endpoints on the pasture boundary. RangeWater calculates the two planning regions and their acreages. The line represents proposed temporary polywire; it does not replace the parent pasture or prove that fence exists."
        ),
        FieldGuideSection(
            "Adding Gates",
            "Select a pasture and choose Add Gate. Place the gate directly on a fence segment. RangeWater preserves the gate's fence anchors, width, type, status, and connectivity. Open or closed status does not yet propagate water access."
        ),
        FieldGuideSection(
            "Adding Water and Understanding Coverage",
            "Green shows the preferred 0-to-800-foot zone. Yellow shows only the 800-to-1,000-foot transition. Physical Radius shows distance geometry; Accessible Coverage clips it to explicitly assigned pastures. Coverage is a planning visualization and does not guarantee animal access."
        ),
        FieldGuideSection(
            "Creating Herds and Recording Movements",
            "Create a herd with count, stock class, weight, marker, and current location. Planning a movement creates no location change. Complete the movement only when it occurs; completion updates herd location and preserves history together."
        ),
        FieldGuideSection(
            "Building Grazing Circuits",
            "A Grazing Circuit is an ordered planning group of pastures. Pastures may carry several seasonal roles. Assigning a herd to a circuit does not move it or prove that circuit pastures are physically connected."
        ),
        FieldGuideSection(
            "Understanding Recorded Rest",
            "Recorded rest begins at the latest completed herd departure from a pasture. Occupied pastures do not display a running rest total. Recorded rest depends on entered movements and does not prove biological recovery or readiness."
        ),
        FieldGuideSection(
            "Recording Forage and Stockpile Observations",
            "Measure representative undisturbed points, enter their average height and sample count, select the stand and condition, and review the calibration. Results are operator-input estimates, not measured feed inventory or feeding recommendations."
        ),
        FieldGuideSection(
            "Creating, Restoring, and Sharing Backups",
            "Use Data Safety to choose a durable folder and create portable .rangewater backups. Restore replaces the complete current ranch after validation. Undo Last Restore returns the pre-restore state. Uninstalling without an external backup can remove local records."
        ),
        FieldGuideSection(
            "Common Problems and Recovery",
            "If imagery is slow, the ranch records remain local and the overview imagery may remain as a fallback. If an edit is rejected, read the message for a gate, shared corner, circuit, herd, movement, forage history, or paddock plan that protects the affected record. Create a backup before large changes."
        ),
        FieldGuideSection(
            "Planning Limits and Important Disclosures",
            "Mapped boundaries and acreage are estimates, not surveys. Water coverage does not guarantee access. Gate-aware propagation, terrain, live GPS, and permanent paddock promotion are not part of 1.2.0. Rest and forage results depend on complete, representative field records."
        )
    )
}
