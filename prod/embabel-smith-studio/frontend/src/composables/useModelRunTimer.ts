/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * The live timer for a model-launching call. The measurement itself belongs to `api/client` — the
 * single `fetch` site — which starts and settles it with the request; this composable just hands
 * that one shared piece of state to whichever component renders it, so a pane never starts or stops
 * a timer of its own.
 */

import { modelTimer } from '../api/client'
import type { ModelRunTimer } from '../api/client'

/** Elapsed milliseconds as `12.4s`, or `1m 02s` once past a minute. */
export function formatElapsed(ms: number): string {
  const seconds = ms / 1000
  if (seconds < 60) return `${seconds.toFixed(1)}s`
  const minutes = Math.floor(seconds / 60)
  const rest = Math.floor(seconds % 60)
  return `${minutes}m ${String(rest).padStart(2, '0')}s`
}

export function useModelRunTimer(): ModelRunTimer {
  return modelTimer
}
