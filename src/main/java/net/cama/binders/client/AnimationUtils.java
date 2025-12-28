package net.cama.binders.client;

import net.cama.binders.Config;

public class AnimationUtils {

    public static float ease(float t, Config.Easing easing) {
        // Clamp t between 0 and 1
        if (t <= 0) return 0;
        if (t >= 1) return 1;

        switch (easing) {
            case LINEAR: return t;
            
            case EASE_IN_SINE: return 1 - (float) Math.cos((t * Math.PI) / 2);
            case EASE_OUT_SINE: return (float) Math.sin((t * Math.PI) / 2);
            case EASE_IN_OUT_SINE: return -(float) (Math.cos(Math.PI * t) - 1) / 2;
            
            case EASE_IN_QUAD: return t * t;
            case EASE_OUT_QUAD: return 1 - (1 - t) * (1 - t);
            case EASE_IN_OUT_QUAD: return t < 0.5 ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
            
            case EASE_IN_CUBIC: return t * t * t;
            case EASE_OUT_CUBIC: return 1 - (float) Math.pow(1 - t, 3);
            case EASE_IN_OUT_CUBIC: return t < 0.5 ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
            
            case EASE_IN_BACK: {
                float c1 = 1.70158f;
                float c3 = c1 + 1;
                return c3 * t * t * t - c1 * t * t;
            }
            case EASE_OUT_BACK: {
                float c1 = 1.70158f;
                float c3 = c1 + 1;
                return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
            }
            case EASE_IN_OUT_BACK: {
                float c1 = 1.70158f;
                float c2 = c1 * 1.525f;
                return t < 0.5
                  ? ((float) Math.pow(2 * t, 2) * ((c2 + 1) * 2 * t - c2)) / 2
                  : ((float) Math.pow(2 * t - 2, 2) * ((c2 + 1) * (t * 2 - 2) + c2) + 2) / 2;
            }
            
            case EASE_OUT_ELASTIC: {
                float c4 = (2 * (float) Math.PI) / 3;
                return t == 0 ? 0 : t == 1 ? 1 : (float) Math.pow(2, -10 * t) * (float) Math.sin((t * 10 - 0.75) * c4) + 1;
            }
            
            default: return t;
        }
    }
}
