"use client";

import { forwardRef, type ComponentPropsWithoutRef } from "react";
import { DropdownMenu as MenuPrimitive } from "radix-ui";
import { Check, Circle } from "lucide-react";
import { cn } from "../../lib/cn";

/*
 * Radix DropdownMenu gives keyboard support (arrows, Home/End, typeahead,
 * Escape), roving focus and focus restore. Menus open on click or keyboard,
 * never on hover.
 */

export const DropdownMenu = MenuPrimitive.Root;
export const DropdownMenuTrigger = MenuPrimitive.Trigger;
export const DropdownMenuGroup = MenuPrimitive.Group;
export const DropdownMenuRadioGroup = MenuPrimitive.RadioGroup;

export const DropdownMenuContent = forwardRef<
  HTMLDivElement,
  ComponentPropsWithoutRef<typeof MenuPrimitive.Content>
>(({ className, sideOffset = 6, align = "end", ...props }, ref) => (
  <MenuPrimitive.Portal>
    <MenuPrimitive.Content
      ref={ref}
      sideOffset={sideOffset}
      align={align}
      className={cn(
        "z-50 min-w-44 overflow-hidden rounded-lg border bg-surface-raised p-1 text-foreground shadow-lg",
        "data-[state=open]:animate-zoom-in data-[state=closed]:animate-zoom-out motion-reduce:animate-none",
        className
      )}
      {...props}
    />
  </MenuPrimitive.Portal>
));
DropdownMenuContent.displayName = "DropdownMenuContent";

const itemClasses = cn(
  "relative flex cursor-default select-none items-center gap-2 rounded-md px-2 py-1.5 text-sm outline-none",
  "focus:bg-muted data-[highlighted]:bg-muted",
  "data-[disabled]:pointer-events-none data-[disabled]:opacity-50",
  "[&_svg]:size-4 [&_svg]:shrink-0 [&_svg]:text-muted-foreground"
);

interface DropdownMenuItemProps extends ComponentPropsWithoutRef<
  typeof MenuPrimitive.Item
> {
  destructive?: boolean;
}

export const DropdownMenuItem = forwardRef<
  HTMLDivElement,
  DropdownMenuItemProps
>(({ className, destructive, ...props }, ref) => (
  <MenuPrimitive.Item
    ref={ref}
    className={cn(
      itemClasses,
      destructive && "text-danger [&_svg]:text-danger",
      className
    )}
    {...props}
  />
));
DropdownMenuItem.displayName = "DropdownMenuItem";

export const DropdownMenuCheckboxItem = forwardRef<
  HTMLDivElement,
  ComponentPropsWithoutRef<typeof MenuPrimitive.CheckboxItem>
>(({ className, children, ...props }, ref) => (
  <MenuPrimitive.CheckboxItem
    ref={ref}
    className={cn(itemClasses, "pl-8", className)}
    {...props}
  >
    <span className="absolute left-2 flex size-4 items-center justify-center">
      <MenuPrimitive.ItemIndicator>
        <Check aria-hidden />
      </MenuPrimitive.ItemIndicator>
    </span>
    {children}
  </MenuPrimitive.CheckboxItem>
));
DropdownMenuCheckboxItem.displayName = "DropdownMenuCheckboxItem";

export const DropdownMenuRadioItem = forwardRef<
  HTMLDivElement,
  ComponentPropsWithoutRef<typeof MenuPrimitive.RadioItem>
>(({ className, children, ...props }, ref) => (
  <MenuPrimitive.RadioItem
    ref={ref}
    className={cn(itemClasses, "pl-8", className)}
    {...props}
  >
    <span className="absolute left-2 flex size-4 items-center justify-center">
      <MenuPrimitive.ItemIndicator>
        <Circle className="size-2 fill-current" aria-hidden />
      </MenuPrimitive.ItemIndicator>
    </span>
    {children}
  </MenuPrimitive.RadioItem>
));
DropdownMenuRadioItem.displayName = "DropdownMenuRadioItem";

export const DropdownMenuLabel = forwardRef<
  HTMLDivElement,
  ComponentPropsWithoutRef<typeof MenuPrimitive.Label>
>(({ className, ...props }, ref) => (
  <MenuPrimitive.Label
    ref={ref}
    className={cn(
      "px-2 py-1.5 text-xs font-medium text-muted-foreground",
      className
    )}
    {...props}
  />
));
DropdownMenuLabel.displayName = "DropdownMenuLabel";

export const DropdownMenuSeparator = forwardRef<
  HTMLDivElement,
  ComponentPropsWithoutRef<typeof MenuPrimitive.Separator>
>(({ className, ...props }, ref) => (
  <MenuPrimitive.Separator
    ref={ref}
    className={cn("-mx-1 my-1 h-px bg-border", className)}
    {...props}
  />
));
DropdownMenuSeparator.displayName = "DropdownMenuSeparator";
