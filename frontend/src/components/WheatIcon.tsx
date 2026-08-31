type Props = {
  size?: number
  color?: string
  strokeWidth?: number
}

export default function WheatIcon({ size = 24, color = 'white', strokeWidth = 1.6 }: Props) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke={color}
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {/* Стебель */}
      <line x1="12" y1="23" x2="12" y2="6" />

      {/* Верхушка — зерно */}
      <path d="M12 6 C10 4.5 10 2 12 1 C14 2 14 4.5 12 6Z" />

      {/* Пара 1 */}
      <path d="M12 8.5 C10 7 7.5 7.5 7 10 C9 10.5 11.5 9.5 12 8.5Z" />
      <path d="M12 8.5 C14 7 16.5 7.5 17 10 C15 10.5 12.5 9.5 12 8.5Z" />

      {/* Пара 2 */}
      <path d="M12 12 C10 10.5 7 11 6.5 13.5 C8.5 14 11.5 13 12 12Z" />
      <path d="M12 12 C14 10.5 17 11 17.5 13.5 C15.5 14 12.5 13 12 12Z" />

      {/* Пара 3 */}
      <path d="M12 15.5 C10 14 6.5 14.5 6 17 C8 17.5 11.5 16.5 12 15.5Z" />
      <path d="M12 15.5 C14 14 17.5 14.5 18 17 C16 17.5 12.5 16.5 12 15.5Z" />

      {/* Пара 4 */}
      <path d="M12 19 C10.5 17.5 7.5 18 7 20 C9 20.5 11.5 19.8 12 19Z" />
      <path d="M12 19 C13.5 17.5 16.5 18 17 20 C15 20.5 12.5 19.8 12 19Z" />
    </svg>
  )
}
