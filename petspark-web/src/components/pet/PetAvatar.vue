<template>
  <figure
    class="pet-avatar"
    :class="`pet-avatar--${shape}`"
    :style="avatarStyle"
  >
    <img :src="resolvedSource" :alt="altText" @error="useFallback" />
  </figure>
</template>

<script>
import catPlaceholder from '@/assets/placeholders/pet-cat.png'
import dogPlaceholder from '@/assets/placeholders/pet-dog.png'
import rabbitPlaceholder from '@/assets/placeholders/pet-rabbit.png'
import birdPlaceholder from '@/assets/placeholders/pet-bird.png'

const PLACEHOLDERS = {
  CAT: catPlaceholder,
  DOG: dogPlaceholder,
  RABBIT: rabbitPlaceholder,
  BIRD: birdPlaceholder,
}

export default {
  name: 'PetAvatar',
  props: {
    pet: { type: Object, default: () => ({}) },
    src: { type: String, default: '' },
    size: { type: Number, default: 72 },
    shape: {
      type: String,
      default: 'round',
      validator: (value) => ['round', 'cover'].includes(value),
    },
  },
  data() {
    return { failed: false }
  },
  computed: {
    fallbackSource() {
      return (
        PLACEHOLDERS[String(this.pet.species || '').toUpperCase()] ||
        dogPlaceholder
      )
    },
    resolvedSource() {
      if (this.failed) return this.fallbackSource
      return (
        this.src ||
        this.pet.coverUrl ||
        this.pet.avatarUrl ||
        this.pet.imageUrl ||
        this.fallbackSource
      )
    },
    altText() {
      return `${this.pet.name || '宠物'}的头像`
    },
    avatarStyle() {
      if (this.shape === 'cover') return null
      return { width: `${this.size}px`, height: `${this.size}px` }
    },
  },
  watch: {
    src() {
      this.failed = false
    },
    pet: {
      deep: true,
      handler() {
        this.failed = false
      },
    },
  },
  methods: {
    useFallback() {
      this.failed = true
    },
  },
}
</script>

<style scoped>
.pet-avatar {
  flex: 0 0 auto;
  margin: 0;
  overflow: hidden;
  background: var(--ps-color-cream);
}

.pet-avatar--round {
  border: 3px solid rgba(255, 255, 255, 0.9);
  border-radius: 50%;
  box-shadow: 0 3px 10px rgba(36, 49, 61, 0.12);
}

.pet-avatar--cover {
  width: 100%;
  aspect-ratio: 4 / 3;
  border-radius: var(--ps-radius-lg) var(--ps-radius-lg) 0 0;
}

.pet-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
