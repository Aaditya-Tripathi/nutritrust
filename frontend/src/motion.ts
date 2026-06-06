import type { Transition, Variants } from 'motion/react'

export const springSoft: Transition = {
  type: 'spring',
  duration: 0.32,
  bounce: 0,
}

export const quickExit: Transition = {
  duration: 0.16,
  ease: [0.4, 0, 1, 1],
}

export const enter = {
  opacity: 1,
  y: 0,
  filter: 'blur(0px)',
}

export const routeInitial = {
  opacity: 0,
  y: 10,
  filter: 'blur(4px)',
}

export const exit = {
  opacity: 0,
  y: -8,
  filter: 'blur(4px)',
  transition: quickExit,
}

export const staggerContainer: Variants = {
  hidden: {},
  visible: {
    transition: {
      staggerChildren: 0.08,
      delayChildren: 0.04,
    },
  },
}

export const staggerItem: Variants = {
  hidden: routeInitial,
  visible: enter,
}

export const scaleFadeInitial = {
  opacity: 0,
  scale: 0.98,
  filter: 'blur(4px)',
}

export const scaleFadeEnter = {
  opacity: 1,
  scale: 1,
  filter: 'blur(0px)',
}

export const scaleFadeExit = {
  opacity: 0,
  scale: 0.98,
  filter: 'blur(4px)',
  transition: quickExit,
}
