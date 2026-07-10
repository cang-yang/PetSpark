<template>
  <article class="service-card">
    <div class="service-card__visual">
      <img
        :src="resolvedImage"
        :alt="`${service.name || '宠物服务'}配图`"
        @error="failed = true"
      />
      <span class="service-card__kind">{{ kindLabel }}</span>
    </div>
    <div class="service-card__body">
      <h2>{{ service.name || '宠物服务' }}</h2>
      <p v-if="service.description" class="service-card__description">
        {{ service.description }}
      </p>
      <p v-if="service.qualification" class="service-card__meta">
        服务资质 · {{ service.qualification }}
      </p>
      <p v-if="service.availabilityNote" class="service-card__meta">
        可约时段 · {{ service.availabilityNote }}
      </p>
      <slot name="details" />
      <p v-if="notice" class="service-card__notice">{{ notice }}</p>
      <footer class="service-card__footer">
        <strong>{{ priceText }}</strong>
        <slot name="actions">
          <router-link v-if="to" class="service-card__action" :to="to">{{
            actionText
          }}</router-link>
          <el-button
            v-else-if="actionText"
            type="primary"
            size="small"
            @click="$emit('action', service)"
            >{{ actionText }}</el-button
          >
        </slot>
      </footer>
    </div>
  </article>
</template>

<script>
import boardingImage from '@/assets/placeholders/service-boarding.png'
import trainingImage from '@/assets/placeholders/service-training.png'
import beautyImage from '@/assets/placeholders/service-beauty.png'
import medicalImage from '@/assets/placeholders/service-medical.png'

const IMAGES = {
  GENERIC: boardingImage,
  BOARDING: boardingImage,
  TRAINING: trainingImage,
  BEAUTY: beautyImage,
  MEDICAL: medicalImage,
}
const LABELS = {
  GENERIC: '日常照护',
  BOARDING: '安心寄养',
  TRAINING: '行为训练',
  BEAUTY: '清洁美容',
  MEDICAL: '线下医疗',
}

export default {
  name: 'ServiceCard',
  props: {
    service: { type: Object, required: true },
    image: { type: String, default: '' },
    to: { type: [String, Object], default: '' },
    actionText: { type: String, default: '查看详情' },
    notice: { type: String, default: '' },
  },
  data() {
    return { failed: false }
  },
  computed: {
    kind() {
      return String(this.service.kind || 'GENERIC').toUpperCase()
    },
    kindLabel() {
      return LABELS[this.kind] || this.kind
    },
    fallbackImage() {
      return IMAGES[this.kind] || boardingImage
    },
    resolvedImage() {
      if (this.failed) return this.fallbackImage
      return (
        this.image ||
        this.service.coverUrl ||
        this.service.imageUrl ||
        this.fallbackImage
      )
    },
    priceText() {
      const value = this.service.basePrice
      return value === undefined || value === null || value === ''
        ? '价格到店确认'
        : `￥${value}`
    },
  },
  watch: {
    image() {
      this.failed = false
    },
    service: {
      deep: true,
      handler() {
        this.failed = false
      },
    },
  },
}
</script>

<style scoped>
.service-card {
  min-width: 0;
  overflow: hidden;
  background: var(--ps-color-surface);
  border: 1px solid var(--ps-color-border);
  border-radius: var(--ps-radius-lg);
  box-shadow: var(--ps-shadow-card);
}
.service-card__visual {
  position: relative;
  aspect-ratio: 3 / 2;
  overflow: hidden;
  background: var(--ps-color-cream);
}
.service-card__visual img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform var(--ps-motion-normal) var(--ps-ease-out);
}
.service-card:hover .service-card__visual img {
  transform: scale(1.025);
}
.service-card__kind {
  position: absolute;
  top: 14px;
  left: 14px;
  padding: 5px 10px;
  color: var(--ps-color-text);
  background: rgba(255, 255, 255, 0.9);
  border-radius: var(--ps-radius-pill);
  font-size: 12px;
  font-weight: 700;
  backdrop-filter: blur(8px);
}
.service-card__body {
  padding: 18px;
}
.service-card h2 {
  margin: 0;
  font-size: 20px;
}
.service-card__description {
  min-height: 48px;
  margin: 10px 0;
  color: var(--ps-color-muted);
  font-size: 14px;
}
.service-card__meta {
  margin: 7px 0;
  color: var(--ps-color-muted);
  font-size: 13px;
}
.service-card__notice {
  margin: 14px 0 0;
  padding: 10px 12px;
  color: var(--ps-color-warning);
  background: #fff8e8;
  border-radius: var(--ps-radius-sm);
  font-size: 12px;
}
.service-card__footer {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-top: 18px;
}
.service-card__footer strong {
  color: var(--ps-color-pink);
  font-size: 20px;
}
.service-card__action {
  color: var(--ps-color-pink);
  font-weight: 700;
  text-decoration: none;
}

@media (prefers-reduced-motion: reduce) {
  .service-card__visual img {
    transition: none;
  }
  .service-card:hover .service-card__visual img {
    transform: none;
  }
}
</style>
