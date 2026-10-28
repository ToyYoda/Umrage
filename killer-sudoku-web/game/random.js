// Nachbau von Kotlins kotlin.random.Random(seed) (XorWow), damit Rätsel-Codes in App und Web
// dasselbe Rätsel ergeben. Alle Rechnungen in 32-Bit-Ganzzahlen wie in Kotlin.

export class KotlinRandom {
  constructor(seed) {
    // Random(seed: Long) = XorWowRandom(seed.toInt(), (seed shr 32).toInt())
    const big = BigInt.asIntN(64, BigInt(seed));
    const seed1 = Number(BigInt.asIntN(32, big));
    const seed2 = Number(BigInt.asIntN(32, big >> 32n));
    this.x = seed1;
    this.y = seed2;
    this.z = 0;
    this.w = 0;
    this.v = ~seed1;
    this.addend = (seed1 << 10) ^ (seed2 >>> 4);
    for (let i = 0; i < 64; i++) this.nextIntRaw();
  }

  nextIntRaw() {
    let t = this.x;
    t = t ^ (t >>> 2);
    this.x = this.y;
    this.y = this.z;
    this.z = this.w;
    const v0 = this.v;
    this.w = v0;
    t = (t ^ (t << 1)) ^ v0 ^ (v0 << 4);
    this.v = t | 0;
    this.addend = (this.addend + 362437) | 0;
    return (t + this.addend) | 0;
  }

  nextBits(bitCount) {
    const value = this.nextIntRaw();
    return (value >>> (32 - bitCount)) & (-bitCount >> 31);
  }

  /** Zufallszahl in 0 until `until` – identisch zu Kotlins Random.nextInt(until). */
  nextInt(until) {
    if (until <= 0) throw new Error('until must be positive');
    const n = until;
    if ((n & -n) === n) return this.nextBits(31 - Math.clz32(n));
    let bits;
    let v;
    do {
      bits = this.nextIntRaw() >>> 1;
      v = bits % n;
    } while (((bits - v + (n - 1)) | 0) < 0);
    return v;
  }
}

/** Kotlins MutableList.shuffle(random): Fisher-Yates von hinten. Liefert eine neue Liste. */
export function shuffled(items, random) {
  const list = items.slice();
  for (let i = list.length - 1; i >= 1; i--) {
    const j = random.nextInt(i + 1);
    const tmp = list[j];
    list[j] = list[i];
    list[i] = tmp;
  }
  return list;
}

/** Kotlins Collection.random(random). */
export function randomElement(items, random) {
  return items[random.nextInt(items.length)];
}
