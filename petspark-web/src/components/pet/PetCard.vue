<template>
  <article class="pet-card">
    <pet-avatar :pet="pet" :src="image" shape="cover" />
    <div class="pet-card__body">
      <div class="pet-card__heading">
        <div>
          <p class="pet-card__eyebrow">{{ speciesLabel(pet.species) }}</p>
          <h2>{{ pet.name || '未命名宠物' }}</h2>
        </div>
        <span v-if="status" class="pet-card__status">{{ status }}</span>
      </div>
      <p class="pet-card__meta">
        {{ pet.breedName || '品种待补充' }}
        <template v-if="pet.age !== undefined && pet.age !== null">
          · {{ pet.age }} 岁</template
        >
      </p>
      <p v-if="description" class="pet-card__description">{{ description }}</p>
      <slot name="details" />
      <footer
        v-if="actionText || detailTo || $slots.actions"
        class="pet-card__actions"
      >
        <slot name="actions">
          <router-link v-if="detailTo" class="pet-card__link" :to="detailTo"
            >查看档案</router-link
          >
          <el-button
            v-if="actionText"
            type="primary"
            size="small"
            data-testid="pet-card-action"
            @click="$emit('action', pet)"
            >{{ actionText }}</el-button
          >
        </slot>
      </footer>
    </div>
  </article>
</template>

<script>
import PetAvatar from './PetAvatar.vue'

export default {
  name: 'PetCard',
  components: { PetAvatar },
  props: {
    pet: { type: Object, required: true },
    image: { type: String, default: '' },
    status: { type: String, default: '' },
    description: { type: String, default: '' },
    actionText: { type: String, default: '' },
    detailTo: { type: [String, Object], default: '' },
  },
  methods: {
    speciesLabel(species) {
      return (
        {
          DOG: '犬类伙伴',
          CAT: '猫咪伙伴',
          RABBIT: '兔兔伙伴',
          BIRD: '鸟类伙伴',
        }[species] ||
        species ||
        '宠物伙伴'
      )
    },
  },
}
</script>

<style scoped>
.pet-card {
  min-width: 0;
  overflow: hidden;
  background: var(--ps-color-surface);
  border: 1px solid var(--ps-color-border);
  border-radius: var(--ps-radius-lg);
  box-shadow: var(--ps-shadow-card);
  transition: transform var(--ps-motion-normal) var(--ps-ease-out),
    box-shadow var(--ps-motion-normal) var(--ps-ease-out);
}

.pet-card:hover {
  box-shadow: var(--ps-shadow-float);
  transform: translateY(-3px);
}

.pet-card__body {
  padding: 18px;
}
.pet-card__heading {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}
.pet-card__heading h2 {
  margin: 2px 0 0;
  font-size: 20px;
  line-height: 1.25;
}
.pet-card__eyebrow {
  margin: 0;
  color: var(--ps-color-peach);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}
.pet-card__status {
  flex: 0 0 auto;
  padding: 4px 9px;
  color: var(--ps-color-green);
  background: rgba(79, 156, 99, 0.1);
  border-radius: var(--ps-radius-pill);
  font-size: 12px;
  font-weight: 700;
}
.pet-card__meta {
  margin: 10px 0 0;
  color: var(--ps-color-muted);
}
.pet-card__description {
  min-height: 48px;
  margin: 12px 0 0;
  color: var(--ps-color-muted);
  font-size: 14px;
}
.pet-card__actions {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: flex-end;
  margin-top: 18px;
}
.pet-card__link {
  margin-right: auto;
  color: var(--ps-color-pink);
  font-weight: 700;
  text-decoration: none;
}

@media (prefers-reduced-motion: reduce) {
  .pet-card {
    transition: none;
  }
  .pet-card:hover {
    transform: none;
  }
}
</style>
