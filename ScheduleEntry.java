package id.dynamicisland.reminder;

/** Satu kegiatan terjadwal, mis. "Berangkat sekolah" jam 06:00. */
public class ScheduleEntry {
    public String id = java.util.UUID.randomUUID().toString();
    public String label = "Kegiatan";
    public String time = "06:00";   // HH:mm (24 jam)
    public int daysMask = 0b1111111; // bit0 = Senin ... bit6 = Minggu
    public boolean enabled = true;

    public boolean runsOn(java.time.DayOfWeek d) {
        return (daysMask & (1 << (d.getValue() - 1))) != 0;
    }

    public String daysLabel() {
        if (daysMask == 0b1111111) return "Setiap hari";
        if (daysMask == 0b0011111) return "Sen-Jum";
        if (daysMask == 0b1100000) return "Sab-Min";
        String[] n = {"Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) if ((daysMask & (1 << i)) != 0) sb.append(sb.length() > 0 ? "," : "").append(n[i]);
        return sb.length() == 0 ? "-" : sb.toString();
    }
}
