"use client";

import type { FieldErrors, UseFormRegister } from "react-hook-form";
import type { CheckoutValues } from "../../schemas/checkout";
import { Field } from "../ui/Field";
import { Input } from "../ui/Input";

/** New shipping address fields inside the checkout form. */
export function AddressFields({
  register,
  errors,
}: {
  register: UseFormRegister<CheckoutValues>;
  errors: FieldErrors<CheckoutValues>["address"];
}) {
  return (
    <div className="grid gap-4 sm:grid-cols-6">
      <Field
        label="Full name"
        error={errors?.recipientName}
        className="sm:col-span-6"
      >
        <Input
          autoComplete="shipping name"
          {...register("address.recipientName")}
        />
      </Field>
      <Field
        label="Street address"
        error={errors?.street}
        className="sm:col-span-6"
      >
        <Input
          autoComplete="shipping address-line1"
          {...register("address.street")}
        />
      </Field>
      <Field
        label="Apartment, suite, etc."
        optional
        error={errors?.street2}
        className="sm:col-span-6"
      >
        <Input
          autoComplete="shipping address-line2"
          {...register("address.street2")}
        />
      </Field>
      <Field label="City" error={errors?.city} className="sm:col-span-3">
        <Input
          autoComplete="shipping address-level2"
          {...register("address.city")}
        />
      </Field>
      <Field
        label="State / province"
        error={errors?.state}
        className="sm:col-span-3"
      >
        <Input
          autoComplete="shipping address-level1"
          {...register("address.state")}
        />
      </Field>
      <Field
        label="Postal code"
        error={errors?.postalCode}
        className="sm:col-span-3"
      >
        <Input
          autoComplete="shipping postal-code"
          {...register("address.postalCode")}
        />
      </Field>
      <Field
        label="Country"
        description="2-letter code, e.g. US"
        error={errors?.country}
        className="sm:col-span-3"
      >
        <Input
          autoComplete="shipping country"
          maxLength={2}
          className="uppercase"
          {...register("address.country")}
        />
      </Field>
      <Field
        label="Phone"
        optional
        error={errors?.phoneNumber}
        className="sm:col-span-6"
      >
        <Input
          type="tel"
          autoComplete="shipping tel"
          {...register("address.phoneNumber")}
        />
      </Field>
    </div>
  );
}
