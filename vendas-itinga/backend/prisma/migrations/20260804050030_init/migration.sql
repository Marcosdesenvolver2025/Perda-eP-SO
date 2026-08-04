-- CreateEnum
CREATE TYPE "UserRole" AS ENUM ('BUYER', 'SELLER', 'COURIER', 'ADMIN');

-- CreateEnum
CREATE TYPE "ProductCondition" AS ENUM ('NEW', 'LIKE_NEW', 'USED');

-- CreateEnum
CREATE TYPE "ProductStatus" AS ENUM ('ACTIVE', 'PAUSED', 'SOLD', 'DELETED');

-- CreateEnum
CREATE TYPE "ShippingMode" AS ENUM ('PLATFORM', 'SELLER');

-- CreateEnum
CREATE TYPE "OrderStatus" AS ENUM ('PENDING_PAYMENT', 'PAID', 'SHIPPED', 'DELIVERED', 'IN_TEST', 'COMPLETED', 'RETURN_REQUESTED', 'RETURN_IN_TRANSIT', 'RETURNED', 'REFUNDED', 'CANCELED');

-- CreateEnum
CREATE TYPE "PaymentMethod" AS ENUM ('CREDIT_CARD', 'PIX');

-- CreateEnum
CREATE TYPE "PaymentStatus" AS ENUM ('PENDING', 'PAID', 'FAILED', 'REFUNDED', 'CHARGEDBACK');

-- CreateEnum
CREATE TYPE "PayoutStatus" AS ENUM ('SCHEDULED', 'RELEASED', 'CANCELED');

-- CreateEnum
CREATE TYPE "DeliveryType" AS ENUM ('DELIVERY', 'RETURN');

-- CreateEnum
CREATE TYPE "DeliveryStatus" AS ENUM ('PENDING', 'ASSIGNED', 'PICKED_UP', 'IN_TRANSIT', 'DELIVERED', 'FAILED');

-- CreateEnum
CREATE TYPE "RefundReason" AS ENUM ('NOT_AS_DESCRIBED', 'DAMAGED', 'WRONG_ITEM', 'NOT_RECEIVED', 'OTHER');

-- CreateEnum
CREATE TYPE "RefundStatus" AS ENUM ('REQUESTED', 'APPROVED', 'REJECTED', 'COLLECTING', 'COMPLETED');

-- CreateTable
CREATE TABLE "User" (
    "id" TEXT NOT NULL,
    "googleId" TEXT,
    "firebaseUid" TEXT,
    "email" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "avatarUrl" TEXT,
    "phone" TEXT,
    "document" TEXT,
    "bio" TEXT,
    "role" "UserRole" NOT NULL DEFAULT 'BUYER',
    "ratingAvg" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "ratingCount" INTEGER NOT NULL DEFAULT 0,
    "pagarmeRecipientId" TEXT,
    "pagarmeRecipientStatus" TEXT,
    "notifyOffers" BOOLEAN NOT NULL DEFAULT true,
    "notifySales" BOOLEAN NOT NULL DEFAULT true,
    "notifyMessages" BOOLEAN NOT NULL DEFAULT true,
    "pushToken" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "User_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "BankAccount" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "holderName" TEXT NOT NULL,
    "holderDocument" TEXT NOT NULL,
    "bankCode" TEXT NOT NULL,
    "branchNumber" TEXT NOT NULL,
    "branchCheckDigit" TEXT,
    "accountNumber" TEXT NOT NULL,
    "accountCheckDigit" TEXT NOT NULL,
    "accountType" TEXT NOT NULL DEFAULT 'checking',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "BankAccount_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Address" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "label" TEXT NOT NULL DEFAULT 'Casa',
    "recipient" TEXT NOT NULL,
    "zipCode" TEXT NOT NULL,
    "street" TEXT NOT NULL,
    "number" TEXT NOT NULL,
    "complement" TEXT,
    "district" TEXT NOT NULL,
    "city" TEXT NOT NULL,
    "state" TEXT NOT NULL,
    "country" TEXT NOT NULL DEFAULT 'BR',
    "latitude" DOUBLE PRECISION,
    "longitude" DOUBLE PRECISION,
    "isDefault" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Address_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Category" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "slug" TEXT NOT NULL,
    "icon" TEXT,
    "parentId" TEXT,

    CONSTRAINT "Category_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Product" (
    "id" TEXT NOT NULL,
    "sellerId" TEXT NOT NULL,
    "categoryId" TEXT,
    "title" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "condition" "ProductCondition" NOT NULL DEFAULT 'USED',
    "size" TEXT,
    "brand" TEXT,
    "priceCents" INTEGER NOT NULL,
    "weightGrams" INTEGER NOT NULL,
    "heightCm" INTEGER NOT NULL,
    "widthCm" INTEGER NOT NULL,
    "lengthCm" INTEGER NOT NULL,
    "shippingMode" "ShippingMode" NOT NULL DEFAULT 'PLATFORM',
    "status" "ProductStatus" NOT NULL DEFAULT 'ACTIVE',
    "latitude" DOUBLE PRECISION,
    "longitude" DOUBLE PRECISION,
    "city" TEXT,
    "state" TEXT,
    "viewCount" INTEGER NOT NULL DEFAULT 0,
    "favoriteCount" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Product_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ProductImage" (
    "id" TEXT NOT NULL,
    "productId" TEXT NOT NULL,
    "url" TEXT NOT NULL,
    "width" INTEGER,
    "height" INTEGER,
    "position" INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT "ProductImage_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Favorite" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "productId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Favorite_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "CartItem" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "productId" TEXT NOT NULL,
    "quantity" INTEGER NOT NULL DEFAULT 1,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "CartItem_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Order" (
    "id" TEXT NOT NULL,
    "checkoutId" TEXT NOT NULL,
    "buyerId" TEXT NOT NULL,
    "sellerId" TEXT NOT NULL,
    "addressId" TEXT NOT NULL,
    "status" "OrderStatus" NOT NULL DEFAULT 'PENDING_PAYMENT',
    "shippingMode" "ShippingMode" NOT NULL,
    "itemsTotalCents" INTEGER NOT NULL,
    "shippingCents" INTEGER NOT NULL,
    "totalCents" INTEGER NOT NULL,
    "platformFeeBps" INTEGER NOT NULL,
    "platformFeeCents" INTEGER NOT NULL,
    "sellerAmountCents" INTEGER NOT NULL,
    "pagarmeOrderId" TEXT,
    "pagarmeChargeId" TEXT,
    "paymentMethod" "PaymentMethod" NOT NULL,
    "paymentStatus" "PaymentStatus" NOT NULL DEFAULT 'PENDING',
    "pixQrCode" TEXT,
    "pixQrCodeUrl" TEXT,
    "pixExpiresAt" TIMESTAMP(3),
    "paidAt" TIMESTAMP(3),
    "shippedAt" TIMESTAMP(3),
    "deliveredAt" TIMESTAMP(3),
    "testEndsAt" TIMESTAMP(3),
    "completedAt" TIMESTAMP(3),
    "canceledAt" TIMESTAMP(3),
    "trackingCode" TEXT,
    "notes" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Order_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "OrderItem" (
    "id" TEXT NOT NULL,
    "orderId" TEXT NOT NULL,
    "productId" TEXT NOT NULL,
    "titleSnapshot" TEXT NOT NULL,
    "imageSnapshot" TEXT,
    "priceCents" INTEGER NOT NULL,
    "quantity" INTEGER NOT NULL DEFAULT 1,

    CONSTRAINT "OrderItem_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "OrderStatusEvent" (
    "id" TEXT NOT NULL,
    "orderId" TEXT NOT NULL,
    "status" "OrderStatus" NOT NULL,
    "note" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "OrderStatusEvent_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Payout" (
    "id" TEXT NOT NULL,
    "orderId" TEXT NOT NULL,
    "sellerId" TEXT NOT NULL,
    "amountCents" INTEGER NOT NULL,
    "platformFeeCents" INTEGER NOT NULL,
    "status" "PayoutStatus" NOT NULL DEFAULT 'SCHEDULED',
    "releaseAt" TIMESTAMP(3) NOT NULL,
    "releasedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Payout_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Delivery" (
    "id" TEXT NOT NULL,
    "orderId" TEXT NOT NULL,
    "type" "DeliveryType" NOT NULL DEFAULT 'DELIVERY',
    "status" "DeliveryStatus" NOT NULL DEFAULT 'PENDING',
    "courierId" TEXT,
    "pickupLabel" TEXT NOT NULL,
    "pickupAddress" TEXT NOT NULL,
    "pickupLatitude" DOUBLE PRECISION,
    "pickupLongitude" DOUBLE PRECISION,
    "dropoffLabel" TEXT NOT NULL,
    "dropoffAddress" TEXT NOT NULL,
    "dropoffLatitude" DOUBLE PRECISION,
    "dropoffLongitude" DOUBLE PRECISION,
    "assignedAt" TIMESTAMP(3),
    "pickedUpAt" TIMESTAMP(3),
    "completedAt" TIMESTAMP(3),
    "failureNote" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Delivery_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "RefundRequest" (
    "id" TEXT NOT NULL,
    "orderId" TEXT NOT NULL,
    "reason" "RefundReason" NOT NULL,
    "description" TEXT NOT NULL,
    "photos" TEXT[],
    "status" "RefundStatus" NOT NULL DEFAULT 'REQUESTED',
    "platformFeeRetainedCents" INTEGER NOT NULL DEFAULT 0,
    "refundedAmountCents" INTEGER NOT NULL DEFAULT 0,
    "reviewedAt" TIMESTAMP(3),
    "completedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "RefundRequest_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Conversation" (
    "id" TEXT NOT NULL,
    "productId" TEXT,
    "buyerId" TEXT NOT NULL,
    "sellerId" TEXT NOT NULL,
    "lastMessageAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Conversation_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Message" (
    "id" TEXT NOT NULL,
    "conversationId" TEXT NOT NULL,
    "senderId" TEXT NOT NULL,
    "body" TEXT NOT NULL,
    "readAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Message_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "WebhookEvent" (
    "id" TEXT NOT NULL,
    "provider" TEXT NOT NULL DEFAULT 'pagarme',
    "externalId" TEXT,
    "type" TEXT NOT NULL,
    "payload" JSONB NOT NULL,
    "processed" BOOLEAN NOT NULL DEFAULT false,
    "error" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "WebhookEvent_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "User_googleId_key" ON "User"("googleId");

-- CreateIndex
CREATE UNIQUE INDEX "User_firebaseUid_key" ON "User"("firebaseUid");

-- CreateIndex
CREATE UNIQUE INDEX "User_email_key" ON "User"("email");

-- CreateIndex
CREATE UNIQUE INDEX "User_pagarmeRecipientId_key" ON "User"("pagarmeRecipientId");

-- CreateIndex
CREATE INDEX "User_email_idx" ON "User"("email");

-- CreateIndex
CREATE UNIQUE INDEX "BankAccount_userId_key" ON "BankAccount"("userId");

-- CreateIndex
CREATE INDEX "Address_userId_idx" ON "Address"("userId");

-- CreateIndex
CREATE UNIQUE INDEX "Category_name_key" ON "Category"("name");

-- CreateIndex
CREATE UNIQUE INDEX "Category_slug_key" ON "Category"("slug");

-- CreateIndex
CREATE INDEX "Product_sellerId_idx" ON "Product"("sellerId");

-- CreateIndex
CREATE INDEX "Product_status_createdAt_idx" ON "Product"("status", "createdAt");

-- CreateIndex
CREATE INDEX "Product_categoryId_idx" ON "Product"("categoryId");

-- CreateIndex
CREATE INDEX "ProductImage_productId_idx" ON "ProductImage"("productId");

-- CreateIndex
CREATE UNIQUE INDEX "Favorite_userId_productId_key" ON "Favorite"("userId", "productId");

-- CreateIndex
CREATE UNIQUE INDEX "CartItem_userId_productId_key" ON "CartItem"("userId", "productId");

-- CreateIndex
CREATE UNIQUE INDEX "Order_pagarmeOrderId_key" ON "Order"("pagarmeOrderId");

-- CreateIndex
CREATE INDEX "Order_buyerId_createdAt_idx" ON "Order"("buyerId", "createdAt");

-- CreateIndex
CREATE INDEX "Order_sellerId_createdAt_idx" ON "Order"("sellerId", "createdAt");

-- CreateIndex
CREATE INDEX "Order_checkoutId_idx" ON "Order"("checkoutId");

-- CreateIndex
CREATE INDEX "Order_status_testEndsAt_idx" ON "Order"("status", "testEndsAt");

-- CreateIndex
CREATE INDEX "OrderItem_orderId_idx" ON "OrderItem"("orderId");

-- CreateIndex
CREATE INDEX "OrderStatusEvent_orderId_idx" ON "OrderStatusEvent"("orderId");

-- CreateIndex
CREATE UNIQUE INDEX "Payout_orderId_key" ON "Payout"("orderId");

-- CreateIndex
CREATE INDEX "Payout_sellerId_status_idx" ON "Payout"("sellerId", "status");

-- CreateIndex
CREATE INDEX "Payout_status_releaseAt_idx" ON "Payout"("status", "releaseAt");

-- CreateIndex
CREATE INDEX "Delivery_status_type_idx" ON "Delivery"("status", "type");

-- CreateIndex
CREATE INDEX "Delivery_courierId_status_idx" ON "Delivery"("courierId", "status");

-- CreateIndex
CREATE UNIQUE INDEX "RefundRequest_orderId_key" ON "RefundRequest"("orderId");

-- CreateIndex
CREATE INDEX "Conversation_buyerId_lastMessageAt_idx" ON "Conversation"("buyerId", "lastMessageAt");

-- CreateIndex
CREATE INDEX "Conversation_sellerId_lastMessageAt_idx" ON "Conversation"("sellerId", "lastMessageAt");

-- CreateIndex
CREATE UNIQUE INDEX "Conversation_productId_buyerId_sellerId_key" ON "Conversation"("productId", "buyerId", "sellerId");

-- CreateIndex
CREATE INDEX "Message_conversationId_createdAt_idx" ON "Message"("conversationId", "createdAt");

-- CreateIndex
CREATE INDEX "WebhookEvent_provider_externalId_idx" ON "WebhookEvent"("provider", "externalId");

-- AddForeignKey
ALTER TABLE "BankAccount" ADD CONSTRAINT "BankAccount_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Address" ADD CONSTRAINT "Address_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Category" ADD CONSTRAINT "Category_parentId_fkey" FOREIGN KEY ("parentId") REFERENCES "Category"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Product" ADD CONSTRAINT "Product_sellerId_fkey" FOREIGN KEY ("sellerId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Product" ADD CONSTRAINT "Product_categoryId_fkey" FOREIGN KEY ("categoryId") REFERENCES "Category"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ProductImage" ADD CONSTRAINT "ProductImage_productId_fkey" FOREIGN KEY ("productId") REFERENCES "Product"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Favorite" ADD CONSTRAINT "Favorite_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Favorite" ADD CONSTRAINT "Favorite_productId_fkey" FOREIGN KEY ("productId") REFERENCES "Product"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "CartItem" ADD CONSTRAINT "CartItem_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "CartItem" ADD CONSTRAINT "CartItem_productId_fkey" FOREIGN KEY ("productId") REFERENCES "Product"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Order" ADD CONSTRAINT "Order_buyerId_fkey" FOREIGN KEY ("buyerId") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Order" ADD CONSTRAINT "Order_sellerId_fkey" FOREIGN KEY ("sellerId") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Order" ADD CONSTRAINT "Order_addressId_fkey" FOREIGN KEY ("addressId") REFERENCES "Address"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "OrderItem" ADD CONSTRAINT "OrderItem_orderId_fkey" FOREIGN KEY ("orderId") REFERENCES "Order"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "OrderItem" ADD CONSTRAINT "OrderItem_productId_fkey" FOREIGN KEY ("productId") REFERENCES "Product"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "OrderStatusEvent" ADD CONSTRAINT "OrderStatusEvent_orderId_fkey" FOREIGN KEY ("orderId") REFERENCES "Order"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Payout" ADD CONSTRAINT "Payout_orderId_fkey" FOREIGN KEY ("orderId") REFERENCES "Order"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Payout" ADD CONSTRAINT "Payout_sellerId_fkey" FOREIGN KEY ("sellerId") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Delivery" ADD CONSTRAINT "Delivery_orderId_fkey" FOREIGN KEY ("orderId") REFERENCES "Order"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Delivery" ADD CONSTRAINT "Delivery_courierId_fkey" FOREIGN KEY ("courierId") REFERENCES "User"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "RefundRequest" ADD CONSTRAINT "RefundRequest_orderId_fkey" FOREIGN KEY ("orderId") REFERENCES "Order"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Conversation" ADD CONSTRAINT "Conversation_productId_fkey" FOREIGN KEY ("productId") REFERENCES "Product"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Conversation" ADD CONSTRAINT "Conversation_buyerId_fkey" FOREIGN KEY ("buyerId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Conversation" ADD CONSTRAINT "Conversation_sellerId_fkey" FOREIGN KEY ("sellerId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Message" ADD CONSTRAINT "Message_conversationId_fkey" FOREIGN KEY ("conversationId") REFERENCES "Conversation"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Message" ADD CONSTRAINT "Message_senderId_fkey" FOREIGN KEY ("senderId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
