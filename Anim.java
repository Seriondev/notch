package id.dynamicisland.render;

import id.dynamicisland.config.IslandConfig;

/**
 * Nilai beranimasi. Perilakunya mengikuti mode performa:
 *  SUPER_LIGHT : langsung loncat ke target (nol kalkulasi animasi)
 *  LIGHT       : lerp eksponensial sederhana
 *  HEAVY       : pegas (spring) dengan sedikit overshoot ala iOS
 */
public class Anim {
    private float value;
    private float velocity;
    private float target;

    public Anim(float initial) {
        this.value = initial;
        this.target = initial;
    }

    public void set(float t) { this.target = t; }
    public float get() { return value; }
    public float getTarget() { return target; }

    public void snap(float v) { value = v; target = v; velocity = 0; }

    /** @param dt detik sejak frame sebelumnya */
    public void update(float dt) {
        IslandConfig cfg = IslandConfig.get();
        dt = Math.min(dt, 0.05f);
        switch (cfg.animMode) {
            case SUPER_LIGHT -> { value = target; velocity = 0; }
            case LIGHT -> {
                float k = 1f - (float) Math.exp(-14f * cfg.animSpeed * dt);
                value += (target - value) * k;
                if (Math.abs(target - value) < 0.05f) value = target;
            }
            case HEAVY -> {
                float stiffness = 260f * cfg.animSpeed * cfg.animSpeed;
                float damping = 20f * cfg.animSpeed;
                float accel = stiffness * (target - value) - damping * velocity;
                velocity += accel * dt;
                value += velocity * dt;
                if (Math.abs(target - value) < 0.05f && Math.abs(velocity) < 0.5f) {
                    value = target;
                    velocity = 0;
                }
            }
        }
    }
}
