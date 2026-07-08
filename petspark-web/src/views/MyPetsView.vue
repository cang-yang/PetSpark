<template>
  <main>
    <h1>我的宠物</h1>
    <form @submit.prevent="submit">
      <input v-model="form.name" placeholder="宠物名称" required />
      <select v-model="form.species">
        <option value="DOG">狗</option>
        <option value="CAT">猫</option>
      </select>
      <button type="submit">新增宠物</button>
    </form>
    <ul v-if="pets.length" data-testid="my-pet-list">
      <li v-for="pet in pets" :key="pet.id" :data-testid="`my-pet-${pet.id}`">
        {{ pet.name }} ({{ pet.species }})
        <router-link :to="`/my/pets/${pet.id}/health`" :data-testid="`pet-health-link-${pet.id}`">健康记录</router-link>
      </li>
    </ul>
    <p v-if="message">{{ message }}</p>
    <p v-else-if="!loading && !pets.length" data-testid="my-pet-empty">暂无宠物，请先新增。</p>
  </main>
</template>

<script>
import { createMyPet, listPets } from '@/api/pets'

export default {
  name: 'MyPetsView',
  data() {
    return {
      form: { name: '', species: 'DOG' },
      message: '',
      pets: [],
      loading: false,
      page: { page: 1, size: 20 }
    }
  },
  created() {
    this.loadPets()
  },
  methods: {
    async loadPets() {
      this.loading = true
      try {
        const response = await listPets({ page: this.page.page, size: this.page.size })
        this.pets = response.data.items || []
      } catch (error) {
        this.message = error.message
      } finally {
        this.loading = false
      }
    },
    async submit() {
      try {
        await createMyPet(this.form)
        this.message = '宠物已保存'
        this.form = { name: '', species: 'DOG' }
        await this.loadPets()
      } catch (error) {
        this.message = error.message
      }
    }
  }
}
</script>