import { describe, expect, it } from 'vitest'
import {
  PICTURE_MAX_SIZE,
  PICTURE_UPLOAD_TIMEOUT_MS,
  validatePictureFile,
} from '../pictureUpload'

describe('pictureUpload utilities', () => {
  it('accepts supported image files within the size limit', () => {
    const file = new File([new Uint8Array(32)], 'sample.PNG', { type: 'image/png' })

    expect(validatePictureFile(file)).toBeUndefined()
  })

  it('rejects unsupported suffixes', () => {
    const file = new File(['content'], 'sample.txt', { type: 'text/plain' })

    expect(validatePictureFile(file)).toContain('仅支持')
  })

  it('rejects oversized images and uses a dedicated upload timeout', () => {
    const oversizedFile = {
      name: 'large.jpg',
      size: PICTURE_MAX_SIZE + 1,
    } as File

    expect(validatePictureFile(oversizedFile)).toContain('2MB')
    expect(PICTURE_UPLOAD_TIMEOUT_MS).toBeGreaterThan(10_000)
  })
})
