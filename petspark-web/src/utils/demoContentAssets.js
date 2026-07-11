const PET_IMAGES = {
  '豆包': '/demo/cards/pet-doubao.webp',
  '橘子': '/demo/cards/pet-juzi.webp',
  '可乐': '/demo/cards/pet-kele.webp',
  '奶糖': '/demo/cards/pet-naitang.webp'
}

const GOODS_IMAGES = {
  'DEMO-DOG-FOOD-01': '/demo/cards/goods-dog-food.webp',
  'DEMO-CAT-FOOD-01': '/demo/cards/goods-cat-food.webp',
  'DEMO-TREAT-01': '/demo/cards/goods-treat.webp',
  'DEMO-BED-01': '/demo/cards/goods-bed.webp',
  'DEMO-LEASH-01': '/demo/cards/goods-leash.webp',
  'DEMO-TOY-01': '/demo/cards/goods-toy.webp',
  'DEMO-CARE-01': '/demo/cards/goods-dental.webp',
  'DEMO-CARE-02': '/demo/cards/goods-comb.webp'
}

export function getDemoPetImage(pet = {}) {
  return PET_IMAGES[pet.name] || ''
}

export function getDemoGoodsImage(goods = {}) {
  return GOODS_IMAGES[goods.sku] || ''
}
