function svgPlaceholder(label, color) {
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="1200" height="900" viewBox="0 0 1200 900">
      <defs>
        <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="${color}" />
          <stop offset="100%" stop-color="#f6e7cf" />
        </linearGradient>
      </defs>
      <rect width="1200" height="900" fill="url(#g)" />
      <circle cx="980" cy="180" r="160" fill="rgba(255,255,255,0.18)" />
      <circle cx="220" cy="740" r="220" fill="rgba(255,255,255,0.12)" />
      <text x="88" y="470" fill="#ffffff" font-size="72" font-family="Arial, sans-serif" font-weight="700">${label}</text>
    </svg>
  `;
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

export const fallbackImage = svgPlaceholder('SuddenFix Demo', '#0f766e');

export const mockProducts = [
  {
    id: 90001,
    name: 'SuddenFix 极速修护套装',
    categoryId: 1,
    mainImage: svgPlaceholder('Repair Kit', '#0f766e'),
    price: 19900,
    stock: 80,
    description: '主打快速修护与日常备货，适合先完成预热后直接抢购。',
    sales: 268
  },
  {
    id: 90002,
    name: '深层保养精华液',
    categoryId: 1,
    mainImage: svgPlaceholder('Care Serum', '#d97706'),
    price: 25900,
    stock: 52,
    description: '适合做活动期重点关注，先预热再下单会更从容。',
    sales: 133
  },
  {
    id: 90003,
    name: '便携急救工具包',
    categoryId: 2,
    mainImage: svgPlaceholder('Quick Aid', '#155e75'),
    price: 8900,
    stock: 150,
    description: '轻量好带，适合随手备一件，活动期下单更划算。',
    sales: 412
  },
  {
    id: 90004,
    name: '居家安心维修卡',
    categoryId: 3,
    mainImage: svgPlaceholder('Service Card', '#7c3aed'),
    price: 12900,
    stock: 36,
    description: '适合居家常备，数量有限，建议先预热再锁定购买。',
    sales: 91
  }
];
