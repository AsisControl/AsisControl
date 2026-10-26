/** Reemplazo de next/link: en las pruebas basta un enlace HTML. */
export default function Link({ href, children, ...props }) {
  return (
    <a href={href} {...props}>
      {children}
    </a>
  );
}
