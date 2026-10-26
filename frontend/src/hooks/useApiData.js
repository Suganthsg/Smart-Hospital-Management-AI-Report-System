import { useEffect, useRef, useState } from 'react'

export function useApiData(loader, dependencies = []) {
  const loaderRef = useRef(loader)
  const [state, setState] = useState({ data: [], error: '', key: null })
  const [version, setVersion] = useState(0)
  const dependencyKey = JSON.stringify(dependencies)
  const requestKey = `${dependencyKey}:${version}`

  useEffect(() => {
    loaderRef.current = loader
  }, [loader])

  useEffect(() => {
    let active = true
    Promise.resolve()
      .then(() => loaderRef.current())
      .then((data) => {
        if (active) setState({ data, error: '', key: requestKey })
      })
      .catch((error) => {
        if (active) setState({ data: [], error: error.message, key: requestKey })
      })
    return () => { active = false }
  }, [requestKey])

  const current = state.key === requestKey
  return {
    data: current ? state.data : [],
    loading: !current,
    error: current ? state.error : '',
    reload: () => setVersion((value) => value + 1),
  }
}
