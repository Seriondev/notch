package id.dynamicisland.music;

/** Satu lagu. Field sederhana supaya mudah disimpan sebagai JSON. */
public class Track {
    public String id;
    public String title;
    public String artist;
    public String album;
    public int durationSec;
    public String streamUrl;   // URL audio online
    public String coverUrl;    // URL cover online
    public String localAudio;  // nama file di folder library (null kalau belum di-download)
    public String localCover;

    public boolean isDownloaded() { return localAudio != null; }

    public String displayName() { return artist + " - " + title; }
}
