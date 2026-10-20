# AsisControl Frontend

Frontend de la primera iteración, desarrollado con Next.js y React.

> **Documento histórico.** Los servicios `mock` descritos abajo se retiraron porque la interfaz ya consume el backend real. La estructura vigente está en `docs/ESTRUCTURA_FRONTEND.md`.

## Base temporal para backend

La interfaz no consume el backend todavía. Los datos de demostración están aislados de las pantallas:

- `src/features/auth/mock-auth-service.ts`: autenticación temporal y credenciales de prueba.
- `src/features/users/mock-user-management-service.ts`: listado, registro, edición, cambio de estado y auditoría de usuarios.
- Los archivos `types.ts` de cada funcionalidad contienen los contratos que debe conservar la futura implementación HTTP.

Para conectar el backend, se reemplazan los servicios `mock` por implementaciones que cumplan sus interfaces (`AuthService` y `UserManagementService`). La UI, sus modelos y sus estados de carga no deberían requerir cambios.

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

You can start editing the page by modifying `app/page.tsx`. The page auto-updates as you edit the file.

This project uses [`next/font`](https://nextjs.org/docs/app/building-your-application/optimizing/fonts) to automatically optimize and load [Geist](https://vercel.com/font), a new font family for Vercel.

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.
