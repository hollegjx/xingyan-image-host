import { describe, it, expect } from 'vitest'
import { collaborationUrl, editDimensions, readEditState } from '../collaboration'

describe('collaboration protocol', () => {
  it('keeps long IDs and deployment base path, without putting identity or edit token in URLs', () => {
    expect(collaborationUrl('9223372036854775807', '/tuku', 'https://app.example.com')).toBe(
      'wss://app.example.com/tuku/api/ws/picture/edit?pictureId=9223372036854775807',
    )
    expect(collaborationUrl('1', '', 'http://localhost:5173')).toContain('ws://localhost:5173/api/')
  })
  it('rejects cross origin and invalid IDs', () => {
    expect(() => collaborationUrl('1', 'https://evil.example', 'https://app.example.com')).toThrow()
    expect(() => collaborationUrl('../a', '', 'https://app.example.com')).toThrow()
  })
  it('keeps the API cookie path on the dedicated root site', () => {
    expect(collaborationUrl('9223372036854775807', '/tuku', 'https://images.example.com')).toBe(
      'wss://images.example.com/tuku/api/ws/picture/edit?pictureId=9223372036854775807',
    )
  })
  it('validates absolute server state and owner token', () => {
    const state = {
      roomId: 'r',
      sourceVersion: 's',
      revision: '8',
      rotation: 3,
      zoom: -8,
      editing: false,
      saving: false,
    }
    expect(readEditState(state)).toEqual(state)
    for (const change of [
      { zoom: 9 },
      { rotation: -1 },
      { revision: 8 },
      { editing: true },
      { saving: 'yes' },
    ]) {
      expect(() => readEditState({ ...state, ...change })).toThrow()
    }
  })
  it('quarter turns swap canvas dimensions', () => {
    expect(editDimensions(640, 480, 1)).toEqual({ width: 480, height: 640 })
    expect(editDimensions(640, 480, 2)).toEqual({ width: 640, height: 480 })
  })
})
