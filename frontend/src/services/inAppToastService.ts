type ToastPayload = {
  title: string;
  body?: string;
  data?: Record<string, string>;
};

type Listener = (payload: ToastPayload) => void;

let listener: Listener | null = null;

export function subscribeToInAppToasts(fn: Listener) {
  listener = fn;
  return () => {
    if (listener === fn) listener = null;
  };
}

export function showInAppToast(payload: ToastPayload) {
  listener?.(payload);
}
