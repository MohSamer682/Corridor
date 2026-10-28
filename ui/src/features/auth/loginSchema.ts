import { z } from 'zod';

export const loginSchema = z.object({
  email: z
    .string()
    .trim()
    .max(254, 'Enter your work email.')
    .pipe(z.email('Enter your work email.')),
  password: z.string().min(8, 'Use 8 to 128 characters.').max(128, 'Use 8 to 128 characters.'),
});

export type LoginValues = z.infer<typeof loginSchema>;
