import { describe, expect, it } from 'vitest'
import { advanceParticles, createParticles, particleCount } from '../ambientParticles'

describe('ambient particle field', () => {
  it('bounds density on desktop and lowers it on small or touch screens', () => {
    expect(particleCount(1440, 1000, false)).toBe(80)
    expect(particleCount(8000, 4000, false)).toBe(80)
    expect(particleCount(390, 844, false)).toBeLessThanOrEqual(24)
    expect(particleCount(1440, 1000, true)).toBe(24)
  })

  it('creates finite particles inside the viewport', () => {
    const particles = createParticles(1200, 800, false, () => 0.4)
    expect(particles).toHaveLength(particleCount(1200, 800, false))
    expect(particles[0]).toMatchObject({ x: 480, y: 320, offsetX: 0, offsetY: 0 })
  })

  it('uses elapsed time and caps jumps after long gaps', () => {
    const a = createParticles(1200, 800, false, () => 0.4)
    const b = structuredClone(a)
    advanceParticles(a, 0.04, 1200, 800, null)
    advanceParticles(b, 0.02, 1200, 800, null)
    advanceParticles(b, 0.02, 1200, 800, null)
    expect(a[0]!.x).toBeCloseTo(b[0]!.x)
    const before = a[0]!.x
    advanceParticles(a, 100, 1200, 800, null)
    expect(Math.abs(a[0]!.x - before)).toBeLessThan(1)
  })

  it('gently repels nearby points and returns when the pointer leaves', () => {
    const particles = createParticles(1000, 800, false, () => 0.5)
    advanceParticles(particles, 0.05, 1000, 800, { x: 470, y: 400 })
    expect(particles[0]!.offsetX).toBeGreaterThan(0)
    expect(particles[0]!.offsetX).toBeLessThan(24)
    const pushed = particles[0]!.offsetX
    advanceParticles(particles, 0.05, 1000, 800, null)
    expect(particles[0]!.offsetX).toBeLessThan(pushed)
    advanceParticles(particles, 0.05, 1000, 800, { x: 500, y: 400 })
    expect(Number.isFinite(particles[0]!.offsetX)).toBe(true)
  })

  it('recycles points leaving the viewport without spawning more', () => {
    const particles = createParticles(1000, 800, false, () => 0.5)
    const count = particles.length
    particles[0]!.x = -30
    particles[0]!.y = 830
    advanceParticles(particles, 0.05, 1000, 800, null)
    expect(particles[0]).toMatchObject({ x: 1020, y: -20 })
    expect(particles).toHaveLength(count)
  })
})
