export interface Point {
  x: number
  y: number
}

export interface AmbientParticle extends Point {
  vx: number
  vy: number
  offsetX: number
  offsetY: number
  radius: number
  tint: number
}

export function particleCount(width: number, height: number, coarse: boolean) {
  const small = coarse || width < 640
  return Math.max(small ? 14 : 28, Math.min(small ? 24 : 80, Math.round((width * height) / 17000)))
}

export function createParticles(
  width: number,
  height: number,
  coarse: boolean,
  random = Math.random,
) {
  return Array.from(
    { length: particleCount(width, height, coarse) },
    (): AmbientParticle => ({
      x: random() * width,
      y: random() * height,
      vx: (random() - 0.5) * 10,
      vy: (random() - 0.5) * 10,
      offsetX: 0,
      offsetY: 0,
      radius: 1 + random() * 1.3,
      tint: Math.floor(random() * 3),
    }),
  )
}

// Drift is time-based, not frame-based. Mouse interaction only adds a small, reversible offset.
export function advanceParticles(
  particles: AmbientParticle[],
  seconds: number,
  width: number,
  height: number,
  pointer: Point | null,
) {
  const dt = Math.max(0, Math.min(seconds, 0.05))
  const ease = 1 - Math.exp(-5 * dt)
  for (const particle of particles) {
    particle.x += particle.vx * dt
    particle.y += particle.vy * dt
    if (particle.x < -20) particle.x = width + 20
    if (particle.x > width + 20) particle.x = -20
    if (particle.y < -20) particle.y = height + 20
    if (particle.y > height + 20) particle.y = -20
    let targetX = 0,
      targetY = 0
    if (pointer) {
      const dx = particle.x - pointer.x
      const dy = particle.y - pointer.y
      const distance = Math.hypot(dx, dy)
      if (distance > 0 && distance < 150) {
        const push = (1 - distance / 150) * 24
        targetX = (dx / distance) * push
        targetY = (dy / distance) * push
      }
    }
    particle.offsetX += (targetX - particle.offsetX) * ease
    particle.offsetY += (targetY - particle.offsetY) * ease
  }
}
