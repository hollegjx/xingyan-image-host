import { defineStore } from 'pinia'
import { ref } from 'vue'

export const PARTICLES_KEY = 'tuku:ambient-particles'
export const MOTION_KEY = 'tuku:reduce-motion'

// Preferences belong to this browser, not an account; never store user data here.
export const usePreferencesStore = defineStore('preferences', () => {
  const particles = ref(true),
    reduceMotion = ref(false),
    persistent = ref(true)
  function read() {
    try {
      particles.value = localStorage.getItem(PARTICLES_KEY) !== 'off'
      reduceMotion.value = localStorage.getItem(MOTION_KEY) === 'on'
    } catch {
      persistent.value = false
    }
  }
  function write(key: string, value: boolean) {
    try {
      localStorage.setItem(key, value ? 'on' : 'off')
      persistent.value = true
    } catch {
      persistent.value = false
    }
  }
  function setParticles(value: boolean) {
    particles.value = value
    write(PARTICLES_KEY, value)
  }
  function setReduceMotion(value: boolean) {
    reduceMotion.value = value
    write(MOTION_KEY, value)
  }
  function reset() {
    setParticles(true)
    setReduceMotion(false)
  }
  function sync(event: StorageEvent) {
    if (event.key === null || [PARTICLES_KEY, MOTION_KEY].includes(event.key)) read()
  }
  read()
  return { particles, reduceMotion, persistent, setParticles, setReduceMotion, reset, sync }
})
