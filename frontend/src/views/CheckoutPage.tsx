"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { MapPin, ShieldAlert, ShoppingBag, Store, Truck } from "lucide-react";
import { addressesApi, addressKeys } from "../api/addresses";
import { ordersApi } from "../api/orders";
import { AddressFields } from "../components/checkout/AddressFields";
import { OrderSummary } from "../components/checkout/OrderSummary";
import { PaymentPanel } from "../components/checkout/PaymentPanel";
import { StepUpPanel } from "../components/checkout/StepUpPanel";
import PickupLocationPicker from "../components/order/PickupLocationPicker";
import ShippingRatePanel from "../components/order/ShippingRatePanel";
import { FormAlert } from "../components/auth/AuthShell";
import { Button } from "../components/ui/Button";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "../components/ui/Card";
import { Checkbox } from "../components/ui/Checkbox";
import { Container } from "../components/ui/Container";
import { EmptyState } from "../components/ui/EmptyState";
import { ErrorState } from "../components/ui/ErrorState";
import { Field } from "../components/ui/Field";
import { Input } from "../components/ui/Input";
import { RadioCard } from "../components/ui/RadioCard";
import { Skeleton } from "../components/ui/Skeleton";
import { useCheckoutSubmit } from "../hooks/useCheckoutSubmit";
import { buildOrderRequest, singleCompanyId } from "../lib/checkout";
import {
  clearCheckoutDraft,
  loadCheckoutDraft,
  saveCheckoutDraft,
} from "../lib/checkoutDraft";
import { formatMoney } from "../lib/format";
import {
  NEW_ADDRESS,
  checkoutSchema,
  emptyAddress,
  type CheckoutValues,
} from "../schemas/checkout";
import { useAppDispatch, useAppSelector } from "../stores/hooks";
import {
  groupByCurrency,
  removeLines,
  selectCartHydrated,
  selectCartLines,
} from "../stores/cartSlice";
import { isDefaultAddress, type CustomerAddress } from "../types/address";
import type { Order } from "../types/order";

const defaults: CheckoutValues = {
  fulfillmentMethod: "DELIVERY",
  addressId: NEW_ADDRESS,
  address: emptyAddress,
  saveAddress: true,
  pickupLocationId: "",
  couponCode: "",
};

function formatAddress(a: CustomerAddress): string {
  return [
    a.street,
    a.street2,
    `${a.city}, ${a.state} ${a.postalCode}`,
    a.country,
  ]
    .filter(Boolean)
    .join(", ");
}

export default function CheckoutPage({
  stripePublishableKey,
}: {
  stripePublishableKey: string | null;
}) {
  const params = useSearchParams();
  const orderId = params.get("order");

  return (
    <Container className="py-8 sm:py-12">
      <h1 className="text-2xl font-semibold tracking-tight text-foreground sm:text-3xl">
        Checkout
      </h1>
      <div className="mt-8">
        {orderId ? (
          <PlacedOrderStep
            orderId={orderId}
            publishableKey={stripePublishableKey}
          />
        ) : (
          <DetailsStep />
        )}
      </div>
    </Container>
  );
}

/* ── Step 1: delivery details, then create the order ───────────────────── */

function DetailsStep() {
  const router = useRouter();
  const params = useSearchParams();
  const dispatch = useAppDispatch();
  const queryClient = useQueryClient();
  const allLines = useAppSelector(selectCartLines);
  const hydrated = useAppSelector(selectCartHydrated);
  const { submit, pending } = useCheckoutSubmit();

  const groups = groupByCurrency(allLines);
  const requested = params.get("currency")?.toUpperCase() ?? null;
  const currency =
    requested && groups.has(requested)
      ? requested
      : ([...groups.keys()][0] ?? null);
  const lines = useMemo(
    () => (currency ? (groups.get(currency) ?? []) : []),
    // groups is rebuilt every render; lines only change with the cart.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [allLines, currency]
  );
  const pickupCompany = singleCompanyId(lines);

  const [formError, setFormError] = useState<string | null>(null);
  const [stepUp, setStepUp] = useState(false);
  const stepUpToken = useRef<string | null>(null);

  const addresses = useQuery({
    queryKey: addressKeys.all,
    queryFn: () => addressesApi.list().then((r) => r.data),
  });
  const saved = useMemo(() => addresses.data ?? [], [addresses.data]);

  const {
    register,
    handleSubmit,
    control,
    setValue,
    reset,
    getValues,
    formState: { errors },
  } = useForm<CheckoutValues>({
    resolver: zodResolver(checkoutSchema),
    defaultValues: defaults,
  });
  const [method, addressId, address, couponCode, pickupLocationId] = useWatch({
    control,
    name: [
      "fulfillmentMethod",
      "addressId",
      "address",
      "couponCode",
      "pickupLocationId",
    ],
  });

  // Preselect the default saved address once addresses load.
  const preselected = useRef(false);
  useEffect(() => {
    if (preselected.current || !addresses.data) return;
    preselected.current = true;
    const preferred =
      addresses.data.find(isDefaultAddress) ?? addresses.data[0];
    if (preferred && getValues("addressId") === NEW_ADDRESS) {
      setValue("addressId", preferred.id);
    }
  }, [addresses.data, getValues, setValue]);

  const destination = useMemo(() => {
    if (method !== "DELIVERY") return undefined;
    const chosen = saved.find((a) => a.id === addressId);
    const source = chosen ?? address;
    return source?.country
      ? {
          country: source.country,
          state: source.state,
          postalCode: source.postalCode,
        }
      : undefined;
  }, [method, saved, addressId, address]);

  // `target` lets the step-up resume submit the currency it saved, before the
  // URL (and so this render's `currency`) has caught up.
  async function place(values: CheckoutValues, target = currency) {
    const targetLines =
      target === currency ? lines : target ? (groups.get(target) ?? []) : [];
    if (!target || targetLines.length === 0) {
      setFormError(
        "The items for this order are no longer in your cart. Review your cart and try again."
      );
      return;
    }
    setFormError(null);
    const request = buildOrderRequest({
      lines: targetLines,
      currency: target,
      values,
      addresses: saved,
      riskVerificationToken: stepUpToken.current,
    });
    const outcome = await submit(request);
    if (!outcome) return;

    if (outcome.kind === "stepUp") {
      stepUpToken.current = null;
      saveCheckoutDraft({ currency: target, values });
      setStepUp(true);
      return;
    }
    if (outcome.kind === "error") {
      setFormError(outcome.message);
      return;
    }

    const order: Order = outcome.order;
    clearCheckoutDraft();
    setStepUp(false);
    dispatch(removeLines(targetLines.map((l) => l.lineId)));
    if (
      values.fulfillmentMethod === "DELIVERY" &&
      values.addressId === NEW_ADDRESS &&
      values.saveAddress
    ) {
      // Best effort, after the order exists so retries never duplicate it.
      addressesApi
        .create({
          ...values.address,
          label: "Shipping",
          country: values.address.country.toUpperCase(),
        })
        .then(() =>
          queryClient.invalidateQueries({ queryKey: addressKeys.all })
        )
        .catch(() => undefined);
    }
    queryClient.setQueryData(["order", order.id], order);
    router.replace(
      order.status === "UNDER_REVIEW"
        ? `/orders/${order.id}`
        : `/checkout?order=${order.id}`
    );
  }

  const onSubmit = handleSubmit((values) => place(values));

  // Returning from the confirmation email: restore the draft and resubmit once,
  // after the cart and saved addresses have loaded (a draft can point at a
  // saved address, which must not fall back to the blank new-address form).
  const resumed = useRef(false);
  useEffect(() => {
    const token = params.get("stepUpToken");
    if (!token || resumed.current || !hydrated || addresses.isPending) return;
    resumed.current = true;
    stepUpToken.current = token;
    const draft = loadCheckoutDraft();
    router.replace(`/checkout${draft ? `?currency=${draft.currency}` : ""}`);
    if (!draft) {
      setFormError(
        "Your order details weren't saved on this device. Enter them again and place your order to confirm it."
      );
      return;
    }
    reset(draft.values);
    const { fulfillmentMethod, addressId: draftAddressId } = draft.values;
    if (
      fulfillmentMethod === "DELIVERY" &&
      draftAddressId !== NEW_ADDRESS &&
      !saved.some((a) => a.id === draftAddressId)
    ) {
      // The token is kept, so confirming the address places the order.
      setFormError(
        "We couldn't load the saved address for this order. Choose your delivery address and place your order to confirm it."
      );
      return;
    }
    void handleSubmit((values) => place(values, draft.currency))();
    // Runs once per token, after the cart and addresses have loaded.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hydrated, params, addresses.isPending]);

  if (!hydrated) {
    return <Skeleton className="h-96 w-full rounded-xl" />;
  }

  if (!currency || lines.length === 0) {
    return (
      <EmptyState
        icon={ShoppingBag}
        title="Your cart is empty"
        description="Add something to your cart to check out."
        action={
          <Button asChild>
            <Link href="/browse">Browse products</Link>
          </Button>
        }
      />
    );
  }

  return (
    <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_380px]">
      <form onSubmit={onSubmit} noValidate className="space-y-6">
        {groups.size > 1 && (
          <FormAlert variant="info">
            Your cart has items in more than one currency. This checkout covers
            the {currency} items; the rest stay in your cart.
          </FormAlert>
        )}
        {stepUp && (
          <StepUpPanel
            busy={pending}
            onToken={(token) => {
              stepUpToken.current = token;
              void onSubmit();
            }}
          />
        )}
        {formError && <FormAlert>{formError}</FormAlert>}

        <Card>
          <CardHeader>
            <CardTitle>How would you like to get your order?</CardTitle>
          </CardHeader>
          <CardContent>
            <fieldset className="grid gap-3 sm:grid-cols-2">
              <legend className="sr-only">Fulfilment method</legend>
              <RadioCard
                value="DELIVERY"
                title={
                  <span className="flex items-center gap-2">
                    <Truck className="size-4" aria-hidden /> Delivery
                  </span>
                }
                description="Shipped to your address"
                {...register("fulfillmentMethod")}
              />
              <RadioCard
                value="PICKUP"
                disabled={!pickupCompany}
                title={
                  <span className="flex items-center gap-2">
                    <Store className="size-4" aria-hidden /> Pickup
                  </span>
                }
                description={
                  pickupCompany
                    ? "Collect from a store location"
                    : "Available when everything comes from one store"
                }
                {...register("fulfillmentMethod")}
              />
            </fieldset>
          </CardContent>
        </Card>

        {method === "DELIVERY" ? (
          <Card>
            <CardHeader>
              <CardTitle>Shipping address</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {addresses.isPending ? (
                <Skeleton className="h-20 w-full rounded-xl" />
              ) : (
                saved.length > 0 && (
                  <fieldset className="grid gap-3">
                    <legend className="sr-only">Saved addresses</legend>
                    {saved.map((a) => (
                      <RadioCard
                        key={a.id}
                        value={a.id}
                        title={
                          <span className="flex items-center gap-2">
                            <MapPin className="size-4" aria-hidden />
                            {a.recipientName}
                            {a.label && (
                              <span className="font-normal text-muted-foreground">
                                · {a.label}
                              </span>
                            )}
                          </span>
                        }
                        description={formatAddress(a)}
                        {...register("addressId")}
                      />
                    ))}
                    <RadioCard
                      value={NEW_ADDRESS}
                      title="Use a new address"
                      {...register("addressId")}
                    />
                  </fieldset>
                )
              )}
              {(addressId === NEW_ADDRESS || saved.length === 0) &&
                !addresses.isPending && (
                  <>
                    <AddressFields
                      register={register}
                      errors={errors.address}
                    />
                    <Checkbox
                      label="Save this address for next time"
                      {...register("saveAddress")}
                    />
                  </>
                )}
            </CardContent>
          </Card>
        ) : (
          pickupCompany && (
            <Card>
              <CardHeader>
                <CardTitle>Pickup location</CardTitle>
              </CardHeader>
              <CardContent className="space-y-2">
                <PickupLocationPicker
                  companyId={pickupCompany}
                  selectedId={pickupLocationId || null}
                  onSelect={(id) =>
                    setValue("pickupLocationId", id, { shouldValidate: true })
                  }
                />
                {errors.pickupLocationId && (
                  <p role="alert" className="text-xs font-medium text-danger">
                    {errors.pickupLocationId.message}
                  </p>
                )}
              </CardContent>
            </Card>
          )
        )}

        <Card>
          <CardHeader>
            <CardTitle>Discount</CardTitle>
          </CardHeader>
          <CardContent>
            <Field label="Coupon code" optional error={errors.couponCode}>
              <Input autoComplete="off" {...register("couponCode")} />
            </Field>
          </CardContent>
        </Card>

        <div className="space-y-3">
          <Button type="submit" size="lg" className="w-full" loading={pending}>
            Continue to payment
          </Button>
          <p className="text-center text-xs text-muted-foreground">
            We reserve your items when you continue. You&apos;ll choose shipping
            and pay on the next step.
          </p>
        </div>
      </form>

      <aside className="lg:sticky lg:top-24 lg:self-start">
        <OrderSummary
          lines={lines}
          currency={currency}
          couponCode={couponCode}
          destination={destination}
        />
      </aside>
    </div>
  );
}

/* ── Step 2: shipping and payment for the reserved order ───────────────── */

function PlacedOrderStep({
  orderId,
  publishableKey,
}: {
  orderId: string;
  publishableKey: string | null;
}) {
  const router = useRouter();
  const order = useQuery({
    queryKey: ["order", orderId],
    queryFn: () => ordersApi.get(orderId).then((r) => r.data),
  });

  const status = order.data?.status;
  useEffect(() => {
    if (status && status !== "RESERVED" && status !== "UNDER_REVIEW") {
      router.replace(`/orders/${orderId}`);
    }
  }, [status, orderId, router]);

  if (order.isPending) {
    return <Skeleton className="h-96 w-full rounded-xl" />;
  }
  if (order.isError || !order.data) {
    return (
      <ErrorState
        title="We couldn't load your order"
        onRetry={() => order.refetch()}
        retrying={order.isFetching}
      />
    );
  }

  const o = order.data;
  if (o.status === "UNDER_REVIEW") {
    return (
      <EmptyState
        icon={ShieldAlert}
        title="Your order is being reviewed"
        description="We're doing a quick security check before taking payment. We'll email you when it's done."
        action={
          <Button asChild>
            <Link href={`/orders/${o.id}`}>View order</Link>
          </Button>
        }
      />
    );
  }

  return (
    <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_380px]">
      <div className="space-y-6">
        <FormAlert variant="success">
          Your items are reserved. Choose shipping and pay to place the order.
        </FormAlert>
        {o.fulfillmentMethod === "DELIVERY" && (
          <ShippingRatePanel order={o} onConfirmed={() => order.refetch()} />
        )}
        <PaymentPanel order={o} publishableKey={publishableKey} />
      </div>
      <aside className="lg:sticky lg:top-24 lg:self-start">
        <Card>
          <CardHeader>
            <CardTitle>Order summary</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <ul className="divide-y">
              {o.items.map((item) => (
                <li key={item.id} className="flex justify-between gap-3 py-2">
                  <span className="min-w-0 text-foreground">
                    {item.bundleName ?? item.productName}{" "}
                    <span className="text-muted-foreground">
                      × {item.quantity}
                    </span>
                    {item.variantTitle && (
                      <span className="block text-xs text-muted-foreground">
                        {item.variantTitle}
                      </span>
                    )}
                  </span>
                  <span className="shrink-0 tabular-nums">
                    {formatMoney(
                      item.unitPrice * item.quantity - item.discountAmount,
                      o.currency
                    )}
                  </span>
                </li>
              ))}
            </ul>
            {o.shippingCostCents > 0 && (
              <div className="flex justify-between border-t pt-3">
                <span className="text-muted-foreground">Shipping</span>
                <span className="tabular-nums">
                  {formatMoney(
                    o.shippingCostCents / 100,
                    o.shippingRateCurrency ?? o.currency
                  )}
                </span>
              </div>
            )}
            <div className="flex justify-between border-t pt-3 font-semibold">
              <span>Total</span>
              <span className="tabular-nums">
                {formatMoney(o.totalAmount, o.currency)}
              </span>
            </div>
          </CardContent>
        </Card>
      </aside>
    </div>
  );
}
