package style

import "github.com/charmbracelet/lipgloss"

var (
	// Brand colors
	Primary   = lipgloss.Color("#7C3AED") // violet
	Secondary = lipgloss.Color("#06B6D4") // cyan
	Success   = lipgloss.Color("#10B981") // green
	Warning   = lipgloss.Color("#F59E0B") // amber
	Error     = lipgloss.Color("#EF4444") // red
	Muted     = lipgloss.Color("#6B7280") // gray

	// Text styles
	Bold      = lipgloss.NewStyle().Bold(true)
	BoldPri   = lipgloss.NewStyle().Bold(true).Foreground(Primary)
	BoldSec   = lipgloss.NewStyle().Bold(true).Foreground(Secondary)
	BoldOk    = lipgloss.NewStyle().Bold(true).Foreground(Success)
	BoldWarn  = lipgloss.NewStyle().Bold(true).Foreground(Warning)
	BoldErr   = lipgloss.NewStyle().Bold(true).Foreground(Error)
	Dim       = lipgloss.NewStyle().Foreground(Muted)

	// Prefix styles for log lines
	KoPrefix  = lipgloss.NewStyle().Bold(true).Foreground(Primary).Render("kọ́")
	InfoTag   = lipgloss.NewStyle().Bold(true).Foreground(Secondary).Render("info")
	OkTag     = lipgloss.NewStyle().Bold(true).Foreground(Success).Render("ready")
	WarnTag   = lipgloss.NewStyle().Bold(true).Foreground(Warning).Render("warn")
	ErrTag    = lipgloss.NewStyle().Bold(true).Foreground(Error).Render("error")
)

// Banner returns the Ko ASCII art banner.
func Banner() string {
	banner := `
  ██╗  ██╗ ██████╗
  ██║ ██╔╝██╔═══██╗
  █████╔╝ ██║   ██║
  ██╔═██╗ ██║   ██║
  ██║  ██╗╚██████╔╝
  ╚═╝  ╚═╝ ╚═════╝`

	return lipgloss.NewStyle().Foreground(Primary).Bold(true).Render(banner)
}

// Tag renders a bracketed colored tag like [info], [ready], etc.
func Tag(label string, color lipgloss.Color) string {
	return lipgloss.NewStyle().
		Foreground(color).
		Bold(true).
		Render("[" + label + "]")
}

// Info prints a styled info line.
func Info(msg string) string {
	return KoPrefix + " " + Tag("info", Secondary) + " " + msg
}

// Ok prints a styled success line.
func Ok(msg string) string {
	return KoPrefix + " " + Tag("ready", Success) + " " + msg
}

// Warn prints a styled warning line.
func Warn(msg string) string {
	return KoPrefix + " " + Tag("warn", Warning) + " " + msg
}

// Err prints a styled error line.
func Err(msg string) string {
	return KoPrefix + " " + Tag("error", Error) + " " + msg
}

// Service renders a service name in secondary color.
func Service(name string) string {
	return BoldSec.Render(name)
}

// Infra renders an infrastructure resource name.
func Infra(name string) string {
	return lipgloss.NewStyle().Foreground(lipgloss.Color("#A78BFA")).Render(name)
}

// Path renders a file path in muted style.
func Path(p string) string {
	return Dim.Render(p)
}

// URL renders a URL in underlined cyan.
func URL(u string) string {
	return lipgloss.NewStyle().Foreground(Secondary).Underline(true).Render(u)
}
