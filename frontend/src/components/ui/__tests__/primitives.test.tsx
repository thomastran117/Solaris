import { useState } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Button } from "../Button";
import { Field } from "../Field";
import { Input } from "../Input";
import Modal from "../Modal";
import { ConfirmDialog } from "../ConfirmDialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "../DropdownMenu";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../Tabs";
import { Pagination } from "../Pagination";
import { Progress } from "../Progress";

describe("Button", () => {
  it("defaults to type=button so it never submits a form by accident", () => {
    render(<Button>Save</Button>);
    expect(screen.getByRole("button", { name: "Save" })).toHaveAttribute(
      "type",
      "button"
    );
  });

  it("disables and marks busy while loading", async () => {
    const onClick = vi.fn();
    render(
      <Button loading onClick={onClick}>
        Save
      </Button>
    );
    const button = screen.getByRole("button", { name: "Save" });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute("aria-busy", "true");
    await userEvent.click(button);
    expect(onClick).not.toHaveBeenCalled();
  });

  it("renders its child with button styles when asChild is set", () => {
    render(
      <Button asChild>
        <a href="/browse">Browse</a>
      </Button>
    );
    const link = screen.getByRole("link", { name: "Browse" });
    expect(link).toHaveAttribute("href", "/browse");
    expect(link.className).toContain("bg-primary");
  });

  it("keeps an asChild link inert while disabled or loading", async () => {
    const onClick = vi.fn();
    const { rerender } = render(
      <Button asChild disabled onClick={onClick}>
        <a href="/checkout">Checkout</a>
      </Button>
    );
    const link = screen.getByRole("link", { name: "Checkout" });
    expect(link).toHaveAttribute("aria-disabled", "true");
    expect(link).toHaveAttribute("tabindex", "-1");
    await userEvent.click(link);
    expect(onClick).not.toHaveBeenCalled();

    rerender(
      <Button asChild loading onClick={onClick}>
        <a href="/checkout">Checkout</a>
      </Button>
    );
    const busy = screen.getByRole("link", { name: "Checkout" });
    expect(busy).toHaveAttribute("aria-busy", "true");
    // The spinner renders inside the slotted element.
    expect(busy.querySelector("svg")).not.toBeNull();
    await userEvent.click(busy);
    expect(onClick).not.toHaveBeenCalled();

    rerender(
      <Button asChild onClick={onClick}>
        <a href="/checkout">Checkout</a>
      </Button>
    );
    await userEvent.click(screen.getByRole("link", { name: "Checkout" }));
    expect(onClick).toHaveBeenCalledOnce();
  });

  it("lets a caller's className override defaults", () => {
    render(<Button className="px-8">Wide</Button>);
    const classes = screen.getByRole("button").className.split(" ");
    expect(classes).toContain("px-8");
    expect(classes).not.toContain("px-4");
  });
});

describe("Field", () => {
  it("links the label, description and error to the control", () => {
    render(
      <Field
        label="Email"
        description="We'll send a receipt here."
        error={{ message: "Enter a valid email" }}
        required
      >
        <Input name="email" />
      </Field>
    );
    const input = screen.getByLabelText(/Email/);
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveAttribute("aria-required", "true");
    expect(input).toHaveAccessibleDescription(
      "We'll send a receipt here. Enter a valid email"
    );
    expect(screen.getByRole("alert")).toHaveTextContent("Enter a valid email");
  });

  it("leaves the control valid without an error", () => {
    render(
      <Field label="Name">
        <Input />
      </Field>
    );
    expect(screen.getByLabelText("Name")).not.toHaveAttribute(
      "aria-invalid",
      "true"
    );
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});

function ModalHarness() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button onClick={() => setOpen(true)}>Open</Button>
      <Modal open={open} onClose={() => setOpen(false)} title="Edit list">
        <Input aria-label="List name" />
      </Modal>
    </>
  );
}

describe("Modal", () => {
  it("traps focus, closes on Escape and restores focus to the trigger", async () => {
    const user = userEvent.setup();
    render(<ModalHarness />);
    const trigger = screen.getByRole("button", { name: "Open" });
    await user.click(trigger);

    const dialog = screen.getByRole("dialog", { name: "Edit list" });
    expect(dialog).toBeInTheDocument();
    expect(dialog).toContainElement(document.activeElement as HTMLElement);

    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it("gives each modal its own title id", () => {
    render(
      <>
        <Modal open onClose={() => {}} title="First">
          one
        </Modal>
        <Modal open onClose={() => {}} title="Second">
          two
        </Modal>
      </>
    );
    const ids = screen
      // The second modal hides the first from assistive tech, hence `hidden`.
      .getAllByRole("heading", { hidden: true })
      .map((heading) => heading.getAttribute("id"));
    expect(new Set(ids).size).toBe(ids.length);
  });
});

describe("ConfirmDialog", () => {
  it("calls onConfirm and leaves closing to the caller", async () => {
    const onConfirm = vi.fn();
    const onOpenChange = vi.fn();
    render(
      <ConfirmDialog
        open
        onOpenChange={onOpenChange}
        title="Delete product?"
        confirmLabel="Delete"
        destructive
        onConfirm={onConfirm}
      />
    );
    expect(screen.getByRole("alertdialog")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Delete" }));
    expect(onConfirm).toHaveBeenCalledOnce();
    expect(onOpenChange).not.toHaveBeenCalledWith(false);
  });
});

describe("DropdownMenu", () => {
  it("opens from the keyboard and activates items", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    render(
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button>Account</Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuItem onSelect={onSelect}>Orders</DropdownMenuItem>
          <DropdownMenuItem>Sign out</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    );
    screen.getByRole("button", { name: "Account" }).focus();
    await user.keyboard("{Enter}");
    const menu = await screen.findByRole("menu");
    expect(menu).toBeInTheDocument();
    await user.keyboard("{Enter}");
    expect(onSelect).toHaveBeenCalledOnce();
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });
});

describe("Tabs", () => {
  it("supports controlled use for URL-driven tabs", async () => {
    const onValueChange = vi.fn();
    render(
      <Tabs value="details" onValueChange={onValueChange}>
        <TabsList aria-label="Product sections">
          <TabsTrigger value="details">Details</TabsTrigger>
          <TabsTrigger value="reviews">Reviews</TabsTrigger>
        </TabsList>
        <TabsContent value="details">Specs</TabsContent>
        <TabsContent value="reviews">Ratings</TabsContent>
      </Tabs>
    );
    expect(screen.getByRole("tab", { name: "Details" })).toHaveAttribute(
      "aria-selected",
      "true"
    );
    await userEvent.click(screen.getByRole("tab", { name: "Reviews" }));
    expect(onValueChange).toHaveBeenCalledWith("reviews");
  });
});

describe("Pagination", () => {
  it("summarises the range and disables out-of-range moves", async () => {
    const onPageChange = vi.fn();
    render(
      <Pagination
        page={0}
        totalPages={3}
        totalItems={45}
        pageSize={20}
        onPageChange={onPageChange}
      />
    );
    expect(screen.getByText("1–20 of 45")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Previous/ })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: /Next/ }));
    expect(onPageChange).toHaveBeenCalledWith(1);
  });
});

describe("Progress", () => {
  it("exposes progressbar semantics and clamps the value", () => {
    const { rerender } = render(
      <Progress
        value={42.4}
        label="Import progress"
        valueText="42 of 100 rows"
      />
    );
    const bar = screen.getByRole("progressbar", { name: "Import progress" });
    expect(bar).toHaveAttribute("aria-valuenow", "42");
    expect(bar).toHaveAttribute("aria-valuetext", "42 of 100 rows");
    const fill = bar.firstElementChild as HTMLElement;
    expect(fill.className).toContain("bg-primary");
    expect(fill.style.width).toBe("42.4%");

    rerender(<Progress value={250} label="Import progress" />);
    expect(screen.getByRole("progressbar")).toHaveAttribute(
      "aria-valuenow",
      "100"
    );
    rerender(<Progress value={Number.NaN} label="Import progress" />);
    expect(screen.getByRole("progressbar")).toHaveAttribute(
      "aria-valuenow",
      "0"
    );
  });
});
