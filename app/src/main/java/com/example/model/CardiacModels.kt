package com.example.model

import androidx.annotation.StringRes
import com.example.R

enum class CardiacPhase(
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int
) {
    ATRIAL_SYSTOLE(
        titleRes = R.string.phase_atrial_systole,
        descRes = R.string.phase_atrial_systole_desc
    ),
    VENTRICULAR_SYSTOLE(
        titleRes = R.string.phase_ventricular_systole,
        descRes = R.string.phase_ventricular_systole_desc
    ),
    DIASTOLE(
        titleRes = R.string.phase_diastole,
        descRes = R.string.phase_diastole_desc
    )
}

enum class TransparencyMode(
    @StringRes val labelRes: Int,
    val defaultOpacity: Float,
    val cutawayAmount: Float
) {
    SOLID(
        labelRes = R.string.mode_solid,
        defaultOpacity = 1.0f,
        cutawayAmount = 0.0f
    ),
    TRANSLUCENT(
        labelRes = R.string.mode_translucent,
        defaultOpacity = 0.34f,
        cutawayAmount = 0.0f
    ),
    CUTAWAY(
        labelRes = R.string.mode_cutaway,
        defaultOpacity = 0.72f,
        cutawayAmount = 1.0f
    )
}

enum class HeartRatePreset(
    @StringRes val labelRes: Int,
    val bpm: Int
) {
    BRADYCARDIA(R.string.preset_bradycardia, 45),
    REST(R.string.preset_rest, 72),
    EXERCISE(R.string.preset_exercise, 125),
    TACHYCARDIA(R.string.preset_tachycardia, 165)
}

enum class BloodCircuitType {
    VENOUS,
    ARTERIAL,
    VALVE_OR_MIXED
}

enum class AnatomyStructureId(
    @StringRes val nameRes: Int,
    @StringRes val shortRes: Int,
    @StringRes val descRes: Int,
    val circuitType: BloodCircuitType,
    val anchorX: Float,
    val anchorY: Float,
    val anchorZ: Float,
    val regionCode: Int
) {
    RIGHT_ATRIUM(
        nameRes = R.string.struct_ra_name,
        shortRes = R.string.struct_ra_short,
        descRes = R.string.struct_ra_desc,
        circuitType = BloodCircuitType.VENOUS,
        anchorX = -0.60f, anchorY = 0.36f, anchorZ = 0.16f,
        regionCode = 3
    ),
    LEFT_ATRIUM(
        nameRes = R.string.struct_la_name,
        shortRes = R.string.struct_la_short,
        descRes = R.string.struct_la_desc,
        circuitType = BloodCircuitType.ARTERIAL,
        anchorX = 0.48f, anchorY = 0.44f, anchorZ = -0.18f,
        regionCode = 4
    ),
    RIGHT_VENTRICLE(
        nameRes = R.string.struct_rv_name,
        shortRes = R.string.struct_rv_short,
        descRes = R.string.struct_rv_desc,
        circuitType = BloodCircuitType.VENOUS,
        anchorX = -0.26f, anchorY = -0.34f, anchorZ = 0.48f,
        regionCode = 2
    ),
    LEFT_VENTRICLE(
        nameRes = R.string.struct_lv_name,
        shortRes = R.string.struct_lv_short,
        descRes = R.string.struct_lv_desc,
        circuitType = BloodCircuitType.ARTERIAL,
        anchorX = 0.44f, anchorY = -0.52f, anchorZ = 0.36f,
        regionCode = 1
    ),
    AORTA(
        nameRes = R.string.struct_aorta_name,
        shortRes = R.string.struct_aorta_short,
        descRes = R.string.struct_aorta_desc,
        circuitType = BloodCircuitType.ARTERIAL,
        anchorX = 0.04f, anchorY = 1.08f, anchorZ = -0.04f,
        regionCode = 6
    ),
    PULMONARY_TRUNK(
        nameRes = R.string.struct_pa_name,
        shortRes = R.string.struct_pa_short,
        descRes = R.string.struct_pa_desc,
        circuitType = BloodCircuitType.VENOUS,
        anchorX = 0.08f, anchorY = 0.68f, anchorZ = 0.28f,
        regionCode = 5
    ),
    VENAE_CAVAE(
        nameRes = R.string.struct_vc_name,
        shortRes = R.string.struct_vc_short,
        descRes = R.string.struct_vc_desc,
        circuitType = BloodCircuitType.VENOUS,
        anchorX = -0.54f, anchorY = 0.96f, anchorZ = -0.02f,
        regionCode = 8
    ),
    PULMONARY_VEINS(
        nameRes = R.string.struct_pv_name,
        shortRes = R.string.struct_pv_short,
        descRes = R.string.struct_pv_desc,
        circuitType = BloodCircuitType.ARTERIAL,
        anchorX = 0.86f, anchorY = 0.44f, anchorZ = -0.34f,
        regionCode = 9
    ),
    TRICUSPID_VALVE(
        nameRes = R.string.struct_tricuspid_name,
        shortRes = R.string.struct_tricuspid_short,
        descRes = R.string.struct_tricuspid_desc,
        circuitType = BloodCircuitType.VENOUS,
        anchorX = -0.40f, anchorY = 0.12f, anchorZ = 0.14f,
        regionCode = 10
    ),
    MITRAL_VALVE(
        nameRes = R.string.struct_mitral_name,
        shortRes = R.string.struct_mitral_short,
        descRes = R.string.struct_mitral_desc,
        circuitType = BloodCircuitType.ARTERIAL,
        anchorX = 0.25f, anchorY = 0.18f, anchorZ = -0.10f,
        regionCode = 10
    ),
    AORTIC_VALVE(
        nameRes = R.string.struct_aortic_valve_name,
        shortRes = R.string.struct_aortic_valve_short,
        descRes = R.string.struct_aortic_valve_desc,
        circuitType = BloodCircuitType.ARTERIAL,
        anchorX = 0.08f, anchorY = 0.26f, anchorZ = 0.04f,
        regionCode = 10
    ),
    PULMONARY_VALVE(
        nameRes = R.string.struct_pulm_valve_name,
        shortRes = R.string.struct_pulm_valve_short,
        descRes = R.string.struct_pulm_valve_desc,
        circuitType = BloodCircuitType.VENOUS,
        anchorX = -0.06f, anchorY = 0.36f, anchorZ = 0.34f,
        regionCode = 10
    )
}

data class ProjectedLabel(
    val id: AnatomyStructureId,
    val normX: Float,
    val normY: Float,
    val ndcDepth: Float,
    val alpha: Float
)

data class HeartUiState(
    val bpm: Int = 72,
    val myocardiumOpacity: Float = TransparencyMode.TRANSLUCENT.defaultOpacity,
    val transparencyMode: TransparencyMode = TransparencyMode.TRANSLUCENT,
    val isPlaying: Boolean = true,
    val showLabels: Boolean = true,
    val selectedStructure: AnatomyStructureId? = null,
    val cameraResetToken: Int = 0
)

data class TelemetrySnapshot(
    val phase: CardiacPhase = CardiacPhase.DIASTOLE,
    val cycleProgress: Float = 0.5f,
    val ecgHistory: List<Float> = emptyList(),
    val atrialPressureNorm: Float = 0.2f,
    val ventricularPressureNorm: Float = 0.1f,
    val projectedLabels: List<ProjectedLabel> = emptyList()
)
