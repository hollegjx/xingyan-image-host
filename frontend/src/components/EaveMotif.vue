<template>
  <svg class="eave-motif" viewBox="0 0 160 52" fill="none" aria-hidden="true">
    <path
      class="roof-line"
      d="m12 40 68-25 68 25M27 43h106"
      stroke="currentColor"
      stroke-linecap="round"
    />
    <path
      class="eave-star"
      d="m121 3 1.7 4.3L127 9l-4.3 1.7L121 15l-1.7-4.3L115 9l4.3-1.7Z"
      fill="currentColor"
    />
    <circle cx="38" cy="16" r="1.25" fill="currentColor" opacity=".35" />
  </svg>
</template>
<style scoped>
.eave-motif {
  width: 116px;
  height: 38px;
  color: #737376;
}
.roof-line {
  opacity: 0.55;
}
.eave-star {
  transform-origin: 121px 9px;
  transition: transform 0.4s ease;
}
@media (hover: hover) and (prefers-reduced-motion: no-preference) {
  .eave-motif:hover .eave-star {
    transform: rotate(90deg);
  }
}
:global(html[data-reduce-motion='true']) .eave-star {
  transition: none;
  transform: none !important;
}
</style>
