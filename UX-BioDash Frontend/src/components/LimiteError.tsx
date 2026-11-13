import { Component, type ErrorInfo, type ReactNode } from 'react'

interface Props {
  /** Al cambiar (p. ej. la ruta), el límite se reinicia y vuelve a intentar renderizar. */
  clave: string
  children: ReactNode
}

interface Estado {
  error: Error | null
  clave: string
}

/** Evita que un fallo en una pantalla deje toda la aplicación en blanco. */
export class LimiteError extends Component<Props, Estado> {
  state: Estado = { error: null, clave: this.props.clave }

  static getDerivedStateFromError(error: Error) {
    return { error }
  }

  static getDerivedStateFromProps(props: Props, estado: Estado) {
    return props.clave !== estado.clave ? { error: null, clave: props.clave } : null
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Error en la pantalla:', error, info.componentStack)
  }

  render() {
    if (!this.state.error) return this.props.children
    return (
      <div className="center-state" style={{ height: '100%' }} role="alert">
        <h2>Algo salió mal en esta pantalla</h2>
        <p className="muted">{this.state.error.message}</p>
        <div className="row">
          <button type="button" className="btn" onClick={() => this.setState({ error: null })}>Reintentar</button>
          <a href="/" className="btn btn-primary">Ir a proyectos</a>
        </div>
      </div>
    )
  }
}
