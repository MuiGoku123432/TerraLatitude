package dev.fnf.terralatitude.mixin;

import com.naturaltemperature.Config;
import com.naturaltemperature.HumidityCalculator;
import dev.fnf.terralatitude.NtSnapshotScope;
import dev.fnf.terralatitude.NtSettingsRedirected;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Reuse NT formulas without assigning its process-global Config fields. */
@Mixin(value = HumidityCalculator.class, remap = false)
public abstract class NtHumiditySettingsMixin implements NtSettingsRedirected {
    @Redirect(method = {"calculateCircular", "calculateCircular6", "calculateSquare6", "calculateBanded"}, at = @At(value = "FIELD",
            target = "Lcom/naturaltemperature/Config;humidity_zone_1:D", opcode = 178), require = 1, remap = false)
    private static double terralatitude$humidity_zone_1() {
        return NtSnapshotScope.number("humidity_zone_1", Config.humidity_zone_1);
    }

    @Redirect(method = {"calculateCircular", "calculateCircular6", "calculateSquare6", "calculateBanded"}, at = @At(value = "FIELD",
            target = "Lcom/naturaltemperature/Config;humidity_zone_2:D", opcode = 178), require = 1, remap = false)
    private static double terralatitude$humidity_zone_2() {
        return NtSnapshotScope.number("humidity_zone_2", Config.humidity_zone_2);
    }

    @Redirect(method = {"calculateCircular", "calculateCircular6", "calculateSquare6", "calculateBanded"}, at = @At(value = "FIELD",
            target = "Lcom/naturaltemperature/Config;humidity_zone_3:D", opcode = 178), require = 1, remap = false)
    private static double terralatitude$humidity_zone_3() {
        return NtSnapshotScope.number("humidity_zone_3", Config.humidity_zone_3);
    }

    @Redirect(method = {"calculateCircular", "calculateCircular6", "calculateSquare6", "calculateBanded"}, at = @At(value = "FIELD",
            target = "Lcom/naturaltemperature/Config;humidity_zone_4:D", opcode = 178), require = 1, remap = false)
    private static double terralatitude$humidity_zone_4() {
        return NtSnapshotScope.number("humidity_zone_4", Config.humidity_zone_4);
    }

    @Redirect(method = {"calculateCircular", "calculateCircular6", "calculateSquare6", "calculateBanded"}, at = @At(value = "FIELD",
            target = "Lcom/naturaltemperature/Config;humidity_zone_5:D", opcode = 178), require = 1, remap = false)
    private static double terralatitude$humidity_zone_5() {
        return NtSnapshotScope.number("humidity_zone_5", Config.humidity_zone_5);
    }

    @Redirect(method = {"calculateCircular", "calculateCircular6", "calculateSquare6", "calculateBanded"}, at = @At(value = "FIELD",
            target = "Lcom/naturaltemperature/Config;humidity_zone_6:D", opcode = 178), require = 1, remap = false)
    private static double terralatitude$humidity_zone_6() {
        return NtSnapshotScope.number("humidity_zone_6", Config.humidity_zone_6);
    }

    @Redirect(method = {"calculateCircular", "calculateCircular6", "calculateSquare6", "calculateBanded"}, at = @At(value = "FIELD",
            target = "Lcom/naturaltemperature/Config;percentage_coverage_zone_1:D", opcode = 178), require = 1, remap = false)
    private static double terralatitude$percentage_coverage_zone_1() {
        return NtSnapshotScope.number("percentage_coverage_zone_1", Config.percentage_coverage_zone_1);
    }

    @Redirect(method = {"calculateCircular", "calculateCircular6", "calculateSquare6", "calculateBanded"}, at = @At(value = "FIELD",
            target = "Lcom/naturaltemperature/Config;percentage_coverage_zone_2:D", opcode = 178), require = 1, remap = false)
    private static double terralatitude$percentage_coverage_zone_2() {
        return NtSnapshotScope.number("percentage_coverage_zone_2", Config.percentage_coverage_zone_2);
    }

    @Redirect(method = {"calculateCircular", "calculateCircular6", "calculateSquare6", "calculateBanded"}, at = @At(value = "FIELD",
            target = "Lcom/naturaltemperature/Config;percentage_coverage_zone_3:D", opcode = 178), require = 1, remap = false)
    private static double terralatitude$percentage_coverage_zone_3() {
        return NtSnapshotScope.number("percentage_coverage_zone_3", Config.percentage_coverage_zone_3);
    }

    @Redirect(method = {"calculateCircular", "calculateCircular6", "calculateSquare6", "calculateBanded"}, at = @At(value = "FIELD",
            target = "Lcom/naturaltemperature/Config;percentage_coverage_zone_4:D", opcode = 178), require = 1, remap = false)
    private static double terralatitude$percentage_coverage_zone_4() {
        return NtSnapshotScope.number("percentage_coverage_zone_4", Config.percentage_coverage_zone_4);
    }

    @Redirect(method = {"calculateCircular", "calculateCircular6", "calculateSquare6", "calculateBanded"}, at = @At(value = "FIELD",
            target = "Lcom/naturaltemperature/Config;percentage_coverage_zone_5:D", opcode = 178), require = 1, remap = false)
    private static double terralatitude$percentage_coverage_zone_5() {
        return NtSnapshotScope.number("percentage_coverage_zone_5", Config.percentage_coverage_zone_5);
    }

    @Redirect(method = {"calculateCircular", "calculateCircular6", "calculateSquare6", "calculateBanded"}, at = @At(value = "FIELD",
            target = "Lcom/naturaltemperature/Config;percentage_coverage_zone_6:D", opcode = 178), require = 1, remap = false)
    private static double terralatitude$percentage_coverage_zone_6() {
        return NtSnapshotScope.number("percentage_coverage_zone_6", Config.percentage_coverage_zone_6);
    }
}
