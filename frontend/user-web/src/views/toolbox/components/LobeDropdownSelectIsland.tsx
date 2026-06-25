import { Button, Dropdown, ThemeProvider } from '@lobehub/ui'
import { ChevronDown } from 'lucide-react'

export interface LobeDropdownOption {
  disabled?: boolean
  label: string
  value: number | string
}

interface Props {
  className?: string
  onChange: (value: number | string) => void
  options: LobeDropdownOption[]
  value: number | string
}

export default function LobeDropdownSelectIsland({
  className,
  onChange,
  options,
  value,
}: Props) {
  const selected = options.find((option) => option.value === value)
  const label = selected?.label || '请选择'

  return (
    <ThemeProvider themeMode="dark">
      <div className={className}>
        <Dropdown
          menu={{
            items: options.map((option) => ({
              disabled: option.disabled,
              key: String(option.value),
              label: option.label,
            })),
            onClick: ({ key }) => {
              const next = options.find((option) => String(option.value) === key)
              if (next && !next.disabled) onChange(next.value)
            },
            selectable: true,
            selectedKeys: [String(value)],
          }}
          placement="topLeft"
          trigger={['click']}
        >
          <Button className="lobe-toolbox-dropdown-button" icon={ChevronDown} iconPlacement="end">
            {label}
          </Button>
        </Dropdown>
      </div>
    </ThemeProvider>
  )
}
