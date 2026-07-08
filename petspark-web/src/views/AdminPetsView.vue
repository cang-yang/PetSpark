<template>
  <main>
    <h1>后台宠物管理</h1>
    <button @click="load">刷新</button>
    <p v-if="error">{{ error }}</p>
    <table>
      <tbody>
        <tr v-for="pet in pets" :key="pet.id">
          <td>{{ pet.name }}</td>
          <td>{{ pet.publicStatus }}</td>
          <td>{{ pet.adoptionStatus }}</td>
        </tr>
      </tbody>
    </table>
  </main>
</template>

<script>
import { listAdminPets } from '@/api/pets'

export default {
  name: 'AdminPetsView',
  data() {
    return { pets: [], error: '' }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      try {
        const response = await listAdminPets({ page: 1, size: 20 })
        this.pets = response.data.data.items || []
      } catch (error) {
        this.error = error.response?.data?.message || '后台宠物加载失败'
      }
    }
  }
}
</script>
