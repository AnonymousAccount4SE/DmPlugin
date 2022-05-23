package com.deeme.tasks;

import com.deeme.types.VerifierChecker;
import com.deeme.types.backpage.HangarChanger;
import com.deeme.types.config.Hour;
import com.deeme.types.config.Profile;
import com.deeme.types.gui.JDayChangeTable;
import com.deeme.types.gui.ShipSupplier;
import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.types.Editor;
import com.github.manolo8.darkbot.config.types.Option;
import com.github.manolo8.darkbot.core.itf.Configurable;
import com.github.manolo8.darkbot.core.itf.InstructionProvider;
import com.github.manolo8.darkbot.core.itf.Task;
import com.github.manolo8.darkbot.core.objects.Gui;
import com.github.manolo8.darkbot.extensions.features.Feature;
import com.github.manolo8.darkbot.utils.AuthAPI;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Feature(name = "WeeklySchedule", description = "Use different module, map for a weekly schedule")
public class WeeklySchedule implements Task, Configurable<WeeklySchedule.WeeklyConfig>, InstructionProvider {

    private WeeklyConfig weeklyConfig;
    private Main main;
    private long lastCheck = 0;
    private HangarChanger hangarChanger;
    private boolean changingHangar = false;
    private boolean stopBot = false;
    private Gui lostConnection;
    Profile profileToUse = null;

    @Override
    public String instructions() {
        return "You have four different profiles. \n" +
                "Each profile can choose which config and map \n";
    }

    @Override
    public void setConfig(WeeklyConfig con) {
        this.weeklyConfig = con;
        setup();
    }

    @Override
    public void install(Main m) {
        if (!Arrays.equals(VerifierChecker.class.getSigners(), getClass().getSigners())) return;
        VerifierChecker.checkAuthenticity();
        this.main = m;
        this.hangarChanger = new HangarChanger(main);
        this.lostConnection = main.guiManager.lostConnection;
        this.lastCheck = 0;
        setup();
    }
    @Override
    public void tickStopped() {
        if (stopBot) tick();
    }

    @Override
    public void tick() {
        if (weeklyConfig.updateHangarList) {
            try {
                main.backpage.hangarManager.updateHangarList();
                if (ShipSupplier.updateOwnedShips(main.backpage.hangarManager.getHangarList().getData().getRet().getShipInfos()))
                    weeklyConfig.updateHangarList = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (main.hero.map.gg) return;

        if (weeklyConfig.changeHangar && profileToUse != null && profileToUse.hangarId != null && !main.config.GENERAL.CURRENT_MODULE.contains("Palladium Hangar")) {
            if (hangarChanger.activeHangar != null) {
                if (!profileToUse.hangarId.equals(hangarChanger.activeHangar)) {
                    if (hangarChanger.isDisconnect()) {
                        hangarChanger.disconnectChangeHangarAndReload(profileToUse.hangarId);
                    } else {
                        hangarChanger.setDisconnectModule("WeeklySchedule: To change hangar");
                    }
                    changingHangar = true;
                    return;
                } else {
                    changingHangar = false;
                }
            } else {
                hangarChanger.updateHangarActive();
                changingHangar = false;
                return;
            }
        }

        if (!changingHangar) {
            updateProfileToUse();

            if (stopBot) {
                if (!main.hero.map.gg && !lostConnection.visible) {
                    hangarChanger.disconectTime = System.currentTimeMillis();
                    hangarChanger.setDisconnectModule("Stop by WeeklySchedule");
                }
            } else if (hangarChanger.disconectTime > 0) {
                hangarChanger.reloadAfterDisconnect(true);
            }
        }

    }

    private void updateProfileToUse() {
        if (lastCheck < System.currentTimeMillis() - 300000) {
            LocalDateTime da = LocalDateTime.now();
            int currentHour = da.getHour();
            Hour hour = this.weeklyConfig.Hours_Changes.get(String.valueOf(currentHour));
            String profile = "";
            if (hour != null) {
                DayOfWeek currentDay = da.getDayOfWeek();
                if (currentDay == DayOfWeek.MONDAY) {
                    profile = hour.mon;
                } else if (currentDay == DayOfWeek.TUESDAY) {
                    profile = hour.tue;
                } else if (currentDay == DayOfWeek.WEDNESDAY) {
                    profile = hour.wed;
                } else if (currentDay == DayOfWeek.THURSDAY) {
                    profile = hour.thu;
                } else if (currentDay == DayOfWeek.FRIDAY) {
                    profile = hour.fri;
                } else if (currentDay== DayOfWeek.SATURDAY) {
                    profile = hour.sat;
                } else if (currentDay == DayOfWeek.SUNDAY) {
                    profile = hour.sun;
                }

                profileToUse = weeklyConfig.profile1;

                if (profile.contains("Stop")) {
                    stopBot = true;
                    return;
                } else {
                    stopBot = false;
                }

                if (profile.contains("P2")) {
                    profileToUse = weeklyConfig.profile2;
                } else if (profile.contains("P3")) {
                    profileToUse = weeklyConfig.profile3;
                } else if (profile.contains("P4")) {
                    profileToUse = weeklyConfig.profile4;
                }

                setProfile();
                lastCheck = System.currentTimeMillis();
            }
        }
    }

    private void setProfile() {
        if (profileToUse != null && !main.hero.map.gg) {
            main.setConfig(profileToUse.BOT_PROFILE);
        }
    }

    private void setup() {
        if (main == null || weeklyConfig == null) return;

        AuthAPI auth = VerifierChecker.getAuthApi();
        if (!auth.isAuthenticated()) auth.setupAuth();

        for (int i=0;i<24;i++){
            String oneHour = String.format("%02d", i);
            Hour hour = this.weeklyConfig.Hours_Changes.get(oneHour);
            if (hour == null) {
                hour = new Hour();
                if (!oneHour.equals("ERROR") && !oneHour.isEmpty()) {
                    weeklyConfig.Hours_Changes.put(oneHour, hour);
                }
            }
        }

        weeklyConfig.updateHangarList = true;
        updateProfileToUse();
    }

    public static class WeeklyConfig {

        @Option()
        @Editor(value = JDayChangeTable.class, shared = true)
        public Map<String, Hour> Hours_Changes = new HashMap<>();

        @Option(value = "Update HangarList", description = "Mark it to update the hangar list")
        public boolean updateHangarList = true;

        @Option(value = "Change hangar", description = "It'll change to the hangar you've put in each profile.")
        public boolean changeHangar = false;

        @Option(value = "Profile 1", description = "To use this profile P1 in the schedule")
        public Profile profile1 = new Profile();

        @Option(value = "Profile 2", description = "To use this profile P2 in the schedule")
        public Profile profile2 = new Profile();

        @Option(value = "Profile 3", description = "To use this profile P3 in the schedule")
        public Profile profile3 = new Profile();

        @Option(value = "Profile 4", description = "To use this profile P4 in the schedule")
        public Profile profile4 = new Profile();
    }
}
