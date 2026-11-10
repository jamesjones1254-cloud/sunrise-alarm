"""Synthesises the wake-up sound: soft wind chimes plus a few songbirds.

Produces a seamless 60-second loop. Run:  python3 tools/make_sound.py out.wav
then encode to OGG for app/src/main/res/raw/dawn_chorus.ogg.
"""
import sys
import wave

import numpy as np

SR = 44100
LENGTH = 60.0
rng = np.random.default_rng(1234)

n = int(SR * LENGTH)
tail = int(SR * 8)
buf = np.zeros(n + tail)


def add(start_s, sig):
    i = int(start_s * SR)
    buf[i:i + len(sig)] += sig[: max(0, len(buf) - i)]


# ---- Chimes: bell-like tones on a pentatonic scale (D major pentatonic, high register)
SCALE = [587.33, 659.25, 739.99, 880.00, 987.77, 1174.66, 1318.51, 1479.98]
PARTIALS = [(1.0, 1.0, 1.0), (2.0, 0.35, 1.6), (3.01, 0.18, 2.4), (4.17, 0.10, 3.3), (5.43, 0.05, 4.5)]


def chime(freq, amp, decay=4.0):
    dur = decay * 1.6
    t = np.arange(int(dur * SR)) / SR
    sig = np.zeros_like(t)
    for ratio, a, dfac in PARTIALS:
        sig += a * np.sin(2 * np.pi * freq * ratio * t + rng.uniform(0, 6.28)) * np.exp(-t * dfac / decay * 1.2)
    attack = np.minimum(1.0, t / 0.004)
    return amp * sig * attack


t_pos = 0.5
while t_pos < LENGTH:
    cluster = rng.integers(1, 4)
    for k in range(cluster):
        f = SCALE[rng.integers(len(SCALE))]
        add(t_pos + k * rng.uniform(0.15, 0.45), chime(f, rng.uniform(0.10, 0.18), rng.uniform(3.0, 5.0)))
    t_pos += rng.uniform(2.2, 5.0)


# ---- Birds
def tone(f0, f1, dur, amp, curve=1.0, vibrato=0.0, vib_rate=0.0):
    t = np.arange(int(dur * SR)) / SR
    x = t / dur
    f = f0 + (f1 - f0) * x ** curve
    if vibrato:
        f = f * (1 + vibrato * np.sin(2 * np.pi * vib_rate * t))
    phase = 2 * np.pi * np.cumsum(f) / SR
    env = np.sin(np.pi * x) ** 1.5
    sig = np.sin(phase) + 0.12 * np.sin(2 * phase)
    return amp * env * sig


def warbler(start, amp):
    """Quick descending trill of short chirps."""
    t = start
    base = rng.uniform(3800, 5200)
    for i in range(rng.integers(5, 10)):
        f0 = base * (1 - 0.025 * i) + rng.uniform(-150, 150)
        d = rng.uniform(0.045, 0.08)
        add(t, tone(f0 * 1.15, f0 * 0.85, d, amp))
        t += d + rng.uniform(0.02, 0.04)
    return t


def whistler(start, amp):
    """Two-note clear whistle, like a 'fee-bee'."""
    hi = rng.uniform(3100, 3500)
    add(start, tone(hi, hi * 0.98, 0.32, amp, vibrato=0.004, vib_rate=30))
    add(start + 0.42, tone(hi * 0.84, hi * 0.80, 0.38, amp * 0.9, vibrato=0.004, vib_rate=30))
    return start + 0.9


def robin(start, amp):
    """Wandering phrases of rising and falling notes."""
    t = start
    for i in range(rng.integers(3, 6)):
        f0 = rng.uniform(2200, 3600)
        f1 = f0 * rng.uniform(0.75, 1.35)
        d = rng.uniform(0.12, 0.25)
        add(t, tone(f0, f1, d, amp, curve=rng.uniform(0.6, 1.6), vibrato=0.01, vib_rate=rng.uniform(40, 70)))
        t += d + rng.uniform(0.05, 0.14)
    return t


def chirp_pair(start, amp):
    f = rng.uniform(5000, 6200)
    add(start, tone(f, f * 0.7, 0.05, amp))
    add(start + 0.11, tone(f * 0.95, f * 0.65, 0.05, amp))
    return start + 0.2


birds = [(warbler, 0.05, (4.0, 9.0)), (whistler, 0.06, (6.0, 12.0)), (robin, 0.05, (3.5, 8.0)), (chirp_pair, 0.035, (2.5, 6.0))]
for fn, amp, (gap_lo, gap_hi) in birds:
    t_pos = rng.uniform(0.5, 5.0)
    while t_pos < LENGTH:
        # occasional distant (quieter) call
        a = amp * (rng.uniform(0.45, 0.7) if rng.random() < 0.35 else rng.uniform(0.85, 1.1))
        end = fn(t_pos, a)
        t_pos = end + rng.uniform(gap_lo, gap_hi)

# ---- Faint airy room tone so silence between sounds isn't dead
noise = rng.normal(0, 1, len(buf))
spec = np.fft.rfft(noise)
freqs = np.fft.rfftfreq(len(noise), 1 / SR)
spec *= 1.0 / (1.0 + (freqs / 600.0) ** 2)  # soft low-pass
air = np.fft.irfft(spec, len(noise))
air *= 0.004 / (np.std(air) + 1e-9)
buf += air

# ---- Wrap the tail onto the start so the loop is seamless
loop = buf[:n].copy()
loop[:tail] += buf[n:]

# Gentle high-shelf softening (one-pole low-pass blended in)
alpha = 0.35
lp = np.empty_like(loop)
acc = 0.0
for i in range(len(loop)):
    acc += alpha * (loop[i] - acc)
    lp[i] = acc
loop = 0.55 * loop + 0.45 * lp

loop /= np.max(np.abs(loop)) / 0.7
pcm = (loop * 32767).astype(np.int16)

out = sys.argv[1] if len(sys.argv) > 1 else "dawn_chorus.wav"
with wave.open(out, "wb") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(pcm.tobytes())
print("wrote", out, f"{LENGTH:.0f}s")
