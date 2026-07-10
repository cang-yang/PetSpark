<template>
  <div
    class="page-atmosphere"
    :class="`page-atmosphere--${scene}`"
    :data-scene="scene"
    aria-hidden="true"
  >
    <div
      v-for="layerScene in renderedScenes"
      :key="layerScene"
      class="page-atmosphere__scene"
      :class="[`page-atmosphere--${layerScene}`, { 'is-active': layerScene === activeScene }]"
      :data-scene-layer="layerScene"
      data-testid="scene-layer"
      @transitionend="handleSceneTransitionEnd($event, layerScene)"
    >
      <div class="page-atmosphere__wash" />
      <img
        v-if="artworkFor(layerScene)"
        class="page-atmosphere__artwork"
        :src="artworkFor(layerScene)"
        alt=""
        data-testid="scene-artwork"
      >
      <div v-else class="page-atmosphere__artwork page-atmosphere__artwork--abstract" data-testid="scene-artwork" />
      <div class="page-atmosphere__spark page-atmosphere__spark--one" />
      <div class="page-atmosphere__spark page-atmosphere__spark--two" />
    </div>
  </div>
</template>

<script>
import heroPets from '@/assets/illustrations/hero-pets.jpg'
import petCat from '@/assets/placeholders/pet-cat.png'
import petDog from '@/assets/placeholders/pet-dog.png'
import serviceBoarding from '@/assets/placeholders/service-boarding.png'
import aiEmptyChat from '@/assets/illustrations/ai-empty-chat.webp'

const ARTWORK = {
  home: heroPets,
  care: petDog,
  companion: petCat,
  service: serviceBoarding,
  ai: aiEmptyChat
}

export default {
  name: 'PageAtmosphere',
  props: {
    scene: {
      type: String,
      default: 'care',
      validator: (value) => ['home', 'care', 'companion', 'service', 'ai', 'admin'].includes(value)
    }
  },
  data() {
    return {
      renderedScenes: [this.scene],
      activeScene: this.scene,
      pointerFrame: null,
      transitionFrame: null,
      cleanupTimer: null,
      disposed: false
    }
  },
  mounted() {
    const reducedMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (!reducedMotion && window.innerWidth > 900 && this.scene !== 'admin') {
      window.addEventListener('pointermove', this.handlePointerMove, { passive: true })
    }
  },
  beforeDestroy() {
    this.disposed = true
    window.removeEventListener('pointermove', this.handlePointerMove)
    if (this.pointerFrame) window.cancelAnimationFrame(this.pointerFrame)
    if (this.transitionFrame) window.cancelAnimationFrame(this.transitionFrame)
    if (this.cleanupTimer) window.clearTimeout(this.cleanupTimer)
  },
  watch: {
    scene(nextScene) {
      this.transitionTo(nextScene)
    }
  },
  methods: {
    artworkFor(scene) {
      return ARTWORK[scene] || ''
    },
    transitionTo(nextScene) {
      if (nextScene === this.activeScene && this.renderedScenes.length === 1) return
      if (this.transitionFrame) window.cancelAnimationFrame(this.transitionFrame)
      if (this.cleanupTimer) window.clearTimeout(this.cleanupTimer)

      this.renderedScenes = [...new Set([this.activeScene, nextScene])]
      this.$nextTick(() => {
        if (this.disposed) return
        this.transitionFrame = window.requestAnimationFrame(() => {
          if (this.disposed) return
          this.activeScene = nextScene
          this.transitionFrame = null
          this.cleanupTimer = window.setTimeout(() => {
            this.finishTransition()
          }, 620)
        })
      })
    },
    handleSceneTransitionEnd(event, layerScene) {
      if (event.target !== event.currentTarget || event.propertyName !== 'opacity') return
      if (layerScene === this.activeScene) this.finishTransition()
    },
    finishTransition() {
      if (this.cleanupTimer) window.clearTimeout(this.cleanupTimer)
      this.cleanupTimer = null
      this.renderedScenes = [this.activeScene]
    },
    handlePointerMove(event) {
      if (this.pointerFrame) return
      this.pointerFrame = window.requestAnimationFrame(() => {
        const x = ((event.clientX / window.innerWidth) - 0.5) * 10
        const y = ((event.clientY / window.innerHeight) - 0.5) * 8
        this.$el.style.setProperty('--ps-scene-x', `${x.toFixed(2)}px`)
        this.$el.style.setProperty('--ps-scene-y', `${y.toFixed(2)}px`)
        this.pointerFrame = null
      })
    }
  }
}
</script>

<style scoped>
.page-atmosphere {
  --ps-scene-x: 0px;
  --ps-scene-y: 0px;
  position: fixed;
  inset: 68px 0 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
  background: #f7f8fb;
}
.page-atmosphere__scene {
  position: absolute;
  inset: 0;
  z-index: 1;
  overflow: hidden;
  opacity: 0;
  background: var(--scene-base, #f7f8fb);
  transform: scale(1.012);
  transition:
    opacity 520ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 620ms cubic-bezier(0.22, 1, 0.36, 1);
  will-change: opacity, transform;
}
.page-atmosphere__scene.is-active {
  z-index: 2;
  opacity: 1;
  transform: scale(1);
}
.page-atmosphere__wash {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 12% 12%, var(--scene-glow-one, rgba(233, 52, 114, 0.12)), transparent 36%),
    radial-gradient(circle at 88% 76%, var(--scene-glow-two, rgba(79, 156, 99, 0.10)), transparent 34%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.76), rgba(255, 255, 255, 0.30));
}
.page-atmosphere__artwork {
  position: absolute;
  top: 3vh;
  right: -5vw;
  width: min(56vw, 820px);
  max-width: none;
  opacity: var(--scene-art-opacity, 0.12);
  filter: saturate(0.78) contrast(0.94);
  -webkit-mask-image: linear-gradient(115deg, transparent 2%, #000 35%, #000 72%, transparent 98%);
  mask-image: linear-gradient(115deg, transparent 2%, #000 35%, #000 72%, transparent 98%);
  transform: translate3d(var(--ps-scene-x), var(--ps-scene-y), 0) scale(1.04);
  transition: transform 420ms var(--ps-ease-out);
}
.page-atmosphere__artwork--abstract {
  top: 8%;
  right: 4%;
  width: min(52vw, 720px);
  aspect-ratio: 1.6;
  opacity: 0.38;
  background:
    linear-gradient(rgba(40, 120, 200, 0.10) 1px, transparent 1px),
    linear-gradient(90deg, rgba(40, 120, 200, 0.10) 1px, transparent 1px),
    radial-gradient(circle at 72% 26%, rgba(40, 120, 200, 0.18), transparent 30%);
  background-size: 42px 42px, 42px 42px, auto;
  -webkit-mask-image: linear-gradient(135deg, transparent, #000 28%, #000 70%, transparent);
  mask-image: linear-gradient(135deg, transparent, #000 28%, #000 70%, transparent);
}
.page-atmosphere__spark {
  position: absolute;
  width: 170px;
  height: 170px;
  border: 1px solid var(--scene-line, rgba(233, 52, 114, 0.12));
  border-radius: 50%;
  opacity: 0.55;
}
.page-atmosphere__spark::before,
.page-atmosphere__spark::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  background: var(--scene-dot, rgba(247, 129, 85, 0.22));
}
.page-atmosphere__spark::before { width: 13px; height: 13px; top: 22px; right: 18px; }
.page-atmosphere__spark::after { width: 7px; height: 7px; bottom: 28px; left: 10px; }
.page-atmosphere__spark--one { top: 13%; left: -78px; }
.page-atmosphere__spark--two { right: 12%; bottom: -112px; width: 240px; height: 240px; }
.page-atmosphere--home { --scene-base: #fff8f4; --scene-glow-one: rgba(233, 52, 114, 0.16); --scene-glow-two: rgba(247, 129, 85, 0.13); --scene-art-opacity: 0.10; }
.page-atmosphere--care { --scene-base: #fff9f5; --scene-glow-one: rgba(233, 52, 114, 0.13); --scene-glow-two: rgba(79, 156, 99, 0.13); --scene-art-opacity: 0.095; }
.page-atmosphere--companion { --scene-base: #f7fbf4; --scene-glow-one: rgba(79, 156, 99, 0.17); --scene-glow-two: rgba(247, 129, 85, 0.12); --scene-art-opacity: 0.11; --scene-line: rgba(79, 156, 99, 0.16); }
.page-atmosphere--service { --scene-base: #fff8f1; --scene-glow-one: rgba(247, 129, 85, 0.16); --scene-glow-two: rgba(233, 52, 114, 0.10); --scene-art-opacity: 0.10; }
.page-atmosphere--ai { --scene-base: #f7f5ff; --scene-glow-one: rgba(114, 95, 197, 0.18); --scene-glow-two: rgba(40, 120, 200, 0.13); --scene-art-opacity: 0.13; --scene-line: rgba(114, 95, 197, 0.17); --scene-dot: rgba(114, 95, 197, 0.24); }
.page-atmosphere--admin { --scene-base: #f4f7fa; --scene-glow-one: rgba(40, 120, 200, 0.10); --scene-glow-two: rgba(33, 49, 66, 0.07); }
@media (max-width: 900px) {
  .page-atmosphere { position: absolute; }
  .page-atmosphere__artwork { top: 5vh; right: -28vw; width: 96vw; opacity: 0.075; transform: none; }
  .page-atmosphere__spark--two { right: -100px; }
}
@media (prefers-reduced-motion: reduce) {
  .page-atmosphere__scene {
    transform: none;
    transition: opacity 80ms linear;
  }
  .page-atmosphere__artwork { transform: none; }
}
</style>
